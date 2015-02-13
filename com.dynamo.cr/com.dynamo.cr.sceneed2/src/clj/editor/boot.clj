(ns editor.boot
  (:require [clojure.java.io :as io]
            [schema.core :as s]
            [internal.clojure :as clojure]
            [dynamo.node :as n]
            [dynamo.system :as ds]
            [dynamo.types :as t]
            [dynamo.file :as f]
            [dynamo.project :as p]
            [internal.system :as is]
            [internal.transaction :as it]
            [internal.disposal :as disp]
            [camel-snake-kebab :as camel]
            [service.log :as log]
            [editor.scene-editor :as es]
            )
  (:import  [com.defold.editor Start UIUtil]
            [java.io File]
            [java.nio.file Paths]
            [javafx.application Platform]
            [javafx.fxml FXMLLoader]
            [javafx.collections FXCollections ObservableList]
            [javafx.scene Scene Node Parent]
            [javafx.geometry Insets]
            [javafx.stage Stage FileChooser]
            [javafx.scene.image Image ImageView WritableImage PixelWriter]
            [javafx.scene.input MouseEvent]
            [javafx.event ActionEvent EventHandler]
            [javafx.scene.control Button Label TextField TitledPane TextArea TreeItem Menu MenuItem MenuBar Tab ProgressBar]
            [javafx.scene.layout AnchorPane GridPane StackPane HBox Priority]
            [javafx.embed.swing SwingFXUtils]
            [javax.media.opengl GL GL2 GLContext GLProfile GLDrawableFactory GLCapabilities]
            [com.jogamp.opengl.util.awt Screenshot]))

(defn- split-ext [f]
  (let [n (.getPath f)
        i (.lastIndexOf n ".")]
    (if (== i -1)
      [n nil]
      [(.substring n 0 i)
       (.substring n (inc i))])))

(defn- relative-path [f1 f2]
  (.toString (.relativize (.toPath f1) (.toPath f2))))

(defn- fill-control [control]
  (AnchorPane/setTopAnchor control 0.0)
  (AnchorPane/setBottomAnchor control 0.0)
  (AnchorPane/setLeftAnchor control 0.0)
  (AnchorPane/setRightAnchor control 0.0))

; ImageView cache
(defonce cached-image-views (atom {}))
(defn- load-image-view [name]
  (if-let [url (io/resource (str "icons/" name))]
    (ImageView. (Image. (str url)))
    (ImageView.)))

(defn- get-image-view [name]
  (if-let [image-view (:name @cached-image-views)]
    image-view
    (let [image-view (load-image-view name)]
      ((swap! cached-image-views assoc name image-view) name))))

; Events
(defmacro event-handler [event & body]
  `(reify EventHandler
     (handle [this ~event]
       ~@body)))

(declare tree-item)

; TreeItem creator
(defn- list-children [parent]
  (if (.isDirectory parent)
    (doto (FXCollections/observableArrayList)
      (.addAll (map tree-item (.listFiles parent))))
    (FXCollections/emptyObservableList)))

; NOTE: Without caching stack-overflow... WHY?
(defn tree-item [parent]
  (let [cached (atom false)]
    (proxy [TreeItem] [parent]
    (isLeaf []
      (.isFile (.getValue this)))
    (getChildren []
      (let [children (proxy-super getChildren)]
        (when-not @cached
          (reset! cached true)
          (.setAll children (list-children (.getValue this))))
        children)))))

(defn- setup-console [root]
  (.appendText (.lookup root "#console") "Hello Console"))

(defmulti create-property-control! (fn [t] t))

(defmethod create-property-control! String [_]
  (let [text (TextField.)
        setter #(.setText text (str %))]
    [text setter]))

(defmethod create-property-control! t/Vec3 [_]
  (let [x (TextField.)
        y (TextField.)
        z (TextField.)
        box (HBox.)
        setter (fn [vec]
                 (.setText x (str (nth vec 0)))
                 (.setText y (str (nth vec 1)))
                 (.setText z (str (nth vec 2))))]
    (doseq [t [x y z]]
      (HBox/setHgrow t Priority/SOMETIMES)
      (.setPrefWidth t 60)
      (.add (.getChildren box) t))
    [box setter]))

(defmethod create-property-control! :default [_]
  (create-property-control! String))

(defn- niceify-label
  [k]
  (-> k
    name
    camel/->Camel_Snake_Case_String
    (clojure.string/replace "_" " ")))

(defn- create-properties-row [grid node key property row]
  (let [label (Label. (niceify-label key))
        [control setter] (create-property-control! (:value-type property))]
    (setter (get node key))
    (GridPane/setConstraints label 1 row)
    (GridPane/setConstraints control 2 row)
    (.add (.getChildren grid) label)
    (.add (.getChildren grid) control)))

(defn- setup-properties [root node]
  (let [properties (t/properties node)
        parent (.lookup root "#properties")
        grid (GridPane.)]
    (.clear (.getChildren parent))
    (.setPadding grid (Insets. 10 10 10 10))
    (.setHgap grid 4)
    (doseq [[key p] properties]
      (let [row (/ (.size (.getChildren grid)) 2)]
        (create-properties-row grid node key p row)))    
    
    (.add (.getChildren parent) grid)))

; Editors
(n/defnode CurveEditor
  (inherits n/Scope)
  (on :create
      (let [btn (Button.)]
        (.setText btn "Curve Editor WIP!")
        (.add (.getChildren (:parent event)) btn)))
  
  t/IDisposable
  (dispose [this]))

(n/defnode TextEditor
  (inherits n/Scope)
  (inherits n/ResourceNode)

  (input text s/Str )
  
  (on :create
      (let [textarea (TextArea.)]
        (fill-control textarea)
        (.appendText textarea (slurp (:file event)))
        (.add (.getChildren (:parent event)) textarea)))
  t/IDisposable
  (dispose [this]
           (println "Dispose TextEditor")))

(n/defnode TextNode
  (inherits n/Scope)
  (inherits n/ResourceNode)
  
  (property text s/Str)
  (property a-vector t/Vec3 (default [1 2 3]))
  
  (on :load
      (ds/set-property self :text (slurp (:filename self)))))

(defn on-edit-text
  [project-node editor-site text-node]
  (let [editor (n/construct TextEditor)]
    (ds/in (ds/add editor)
           (ds/connect text-node :text editor :text)
           editor)))

(defrecord ProjectPath [project-ref ^String path ^String ext]
  t/PathManipulation
  (extension         [this]         ext)
  (replace-extension [this new-ext] (ProjectPath. project-ref path new-ext))
  (local-path        [this]         (if ext (str path "." ext) path))
  (local-name        [this]         (str (last (clojure.string/split path (java.util.regex.Pattern/compile java.io.File/separator))) "." ext))

  f/ProjectRelative
  (project-file          [this]      (io/file (str (:content-root project-ref) "/" (t/local-path this))))

  io/IOFactory
  (io/make-input-stream  [this opts] (io/make-input-stream (f/project-file this) opts))
  (io/make-reader        [this opts] (io/make-reader (io/make-input-stream this opts) opts))
  (io/make-output-stream [this opts] (io/make-output-stream (f/project-file this) opts))
  (io/make-writer        [this opts] (io/make-writer (io/make-output-stream this opts) opts)))

(defn- make-project-path [game-project name]
  (let [f (io/file name)
        [path ext] (split-ext f)]
    (ProjectPath. game-project path ext)))

(n/defnode GameProject
  (inherits n/Scope)
  
  (property node-types         {s/Str s/Symbol})
  ;TODO: Resource type instead of string?
  (property content-root File)

  ; TODO: Couldn't get ds/query to work
   t/NamingContext
  (lookup [this name]
    (let [path (if (instance? ProjectPath name) name (make-project-path this name))]
      (->> (:nodes (:graph @(:world-ref this)))
        (vals)
        (filter #(= path (:filename %)))
        (first))))

  t/IDisposable
  (dispose [this]
           (println "Dispose GameProject"))

  (on :destroy
      (println "Destory GameProject")
      (ds/delete self)))

(def editor-fns {:atlas es/construct-scene-editor})

(defn- find-editor-fn [file]
  (let [ext (last (.split file "\\."))
        editor-fn (if ext ((keyword ext) editor-fns) nil)]
    (or editor-fn on-edit-text)))

(defn- create-editor [game-project file root node-type]
  (let [tab-pane (.lookup root "#editor-tabs")
        parent (AnchorPane.)
        path (relative-path (:content-root game-project) file)
        resource-node (t/lookup game-project path)
        node (ds/transactional 
               (ds/in game-project
                      (let [editor-fn (find-editor-fn (.getName file))]
                        (editor-fn game-project nil resource-node))))
        close-handler (event-handler event
                        (ds/transactional 
                          (ds/delete node)))]

    
    (if (satisfies? t/MessageTarget node)
      (let [tab (Tab. (.getName file))]
        (setup-properties root resource-node)
        
        (.setOnClosed tab close-handler)
        (.setGraphic tab (get-image-view "cog.png"))
        (n/dispatch-message node :create :parent parent :file file)
        (.setContent tab parent)
        (.add (.getTabs tab-pane) tab)
        (.select (.getSelectionModel tab-pane) tab))
      (println "No editor for " node))))

(defn- setup-assets-browser [game-project root]
  (let [tree (.lookup root "#assets")
        tab-pane (.lookup root "#editor-tabs")
        handler (reify EventHandler
                  (handle [this e]
                    (when (= 2 (.getClickCount e))
                      (let [item (-> tree (.getSelectionModel) (.getSelectedItem))
                            file (.getValue item)]
                        (when (.isFile file)
                          (create-editor game-project file root TextEditor))))))]
    (.setOnMouseClicked tree handler)
    (.setCellFactory tree (UIUtil/newFileCellFactory))
    (.setRoot tree (tree-item (:content-root game-project)))))

(defn- bind-menus [menu handler]
  (cond
    (instance? MenuBar menu) (doseq [m (.getMenus menu)] (bind-menus m handler))
    (instance? Menu menu) (doseq [m (.getItems menu)]
                            (.addEventHandler m ActionEvent/ACTION handler))))



(def system nil)
;(is/stop)
(def the-system (is/start))
(def the-root (atom nil))

(defn load-stage [game-project]
  (let [root (FXMLLoader/load (io/resource "editor.fxml"))
        stage (Stage.)
        scene (Scene. root)]
    (.setUseSystemMenuBar (.lookup root "#menu-bar") true)
    (.setTitle stage "Defold Editor 2.0!")
    (.setScene stage scene)

    (.show stage)
    (let [handler (event-handler event (println event))]
      (bind-menus (.lookup root "#menu-bar") handler))
    
    (let [close-handler (event-handler event
                          (ds/transactional 
                            (ds/delete game-project))
                          (disp/dispose-pending (:state (:world the-system))))
          dispose-handler (event-handler event (disp/dispose-pending (:state (:world  the-system))))]
      (.addEventFilter stage MouseEvent/MOUSE_MOVED dispose-handler)
      (.setOnCloseRequest stage close-handler))
    (setup-console root)
    (setup-assets-browser game-project root)
    (reset! the-root root)
    root))

;(.setProgress (.lookup @the-root "#progress-bar") 1.0)

(defn- create-view [game-project root place node-type]
  (let [node (ds/transactional
               (ds/in game-project
                      (ds/add
                        (n/construct node-type))))]
    (n/dispatch-message node :create :parent (.lookup root place))))

(defn load-resource-nodes
  [game-project paths ^ProgressBar progress-bar]
  (ds/transactional
    (ds/in game-project
        [game-project (doall
                        (for [p paths]
                          (p/load-resource game-project p)))])))

(defn get-project-paths [game-project content-root]
  (->> (file-seq content-root)
    (filter #(.isFile %))
    (map #(io/file (relative-path content-root %)))
    (remove #(.startsWith (.getPath %) "."))
    (remove #(.startsWith (.getPath %) "build"))
    (map (fn [f] (make-project-path game-project (.getPath f))))))

(defn- post-load
  [message project-node resource-nodes]
  (doseq [resource-node resource-nodes]
    (log/logging-exceptions (str message (:filename resource-node))
      (when (satisfies? t/MessageTarget resource-node)
        (ds/in project-node
          (t/process-one-event resource-node {:type :load :project project-node}))))))

(defn load-project
  [^File game-project-file]
  (let [progress-bar nil
        content-root (.getParentFile game-project-file)
        game-project (ds/transactional
                       (ds/add
                         (n/construct GameProject
                                      :node-types {"script" TextNode "clj" clojure/ClojureSourceNode}
                                      :content-root content-root)))
        resources       (get-project-paths game-project content-root)
        _ (apply post-load "Loading" (load-resource-nodes game-project resources progress-bar))
        root (load-stage game-project)
        curve (create-view game-project root "#curve-editor-container" CurveEditor)]))

(Platform/runLater 
  (fn [] 
    (load-project (io/file "/Users/ragnarsvensson/eclipse44/branches/1645/1144/test/game.project"))))

