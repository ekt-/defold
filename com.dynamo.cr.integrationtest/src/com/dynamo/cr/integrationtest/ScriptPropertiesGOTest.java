package com.dynamo.cr.integrationtest;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.junit.Test;

import com.dynamo.cr.go.core.GameObjectNode;
import com.dynamo.cr.go.core.RefComponentNode;

public class ScriptPropertiesGOTest extends AbstractSceneTest {

    @Override
    public void setup() throws CoreException, IOException {
        super.setup();

        getPresenter().onLoad("go", new ByteArrayInputStream("components { id: \"script\" component: \"/script/props.script\"}".getBytes()));
    }

    private void saveScript(String path, String content) throws IOException, CoreException {
        IFile file = getContentRoot().getFile(new Path(path));
        InputStream stream = new ByteArrayInputStream(content.getBytes());
        if (!file.exists()) {
            file.create(stream, true, null);
        } else {
            file.setContents(stream, 0, null);
        }
    }

    // Tests

//    @Test
//    public void testReloadComponentFromFile() throws Exception {
//        String path = "/sprite/reload.sprite";
//        String tileSet = "/tileset/test.tileset";
//        String defaultAnimation = "test";
//
//        when(getPresenterContext().selectFile(anyString(), any(String[].class))).thenReturn(path);
//        SpriteNode componentType = new SpriteNode();
//        componentType.setTileSource(tileSet);
//        componentType.setDefaultAnimation(defaultAnimation);
//
//        saveSpriteComponent(path, "", "");
//
//        GameObjectNode go = (GameObjectNode)getModel().getRoot();
//        RefComponentNode component = new RefComponentNode(new SpriteNode());
//        component.setComponent(path);
//        AddComponentOperation op = new AddComponentOperation(go, component, getPresenterContext());
//        getModel().executeOperation(op);
//        assertThat(go.getChildren().size(), is(1));
//        assertNodePropertyStatus(component, "component", IStatus.ERROR, null);
//        ComponentTypeNode type = component.getType();
//
//        saveSpriteComponent(path, tileSet, defaultAnimation);
//
//        assertNodePropertyStatus(component, "component", IStatus.OK, null);
//        assertThat((RefComponentNode)go.getChildren().get(0), is(component));
//        assertThat(type, is(not(component.getType())));
//    }

    @Test
    public void testAccess() throws Exception {

        GameObjectNode gameObject = (GameObjectNode)getModel().getRoot();
        RefComponentNode component = (RefComponentNode)gameObject.getChildren().get(0);

        // Default value
        assertEquals("1", getNodeProperty(component, "number"));
        assertEquals("hash", getNodeProperty(component, "hash"));
        assertEquals("", getNodeProperty(component, "url"));

        // Set value
        setNodeProperty(component, "number", "2");
        assertEquals("2", getNodeProperty(component, "number"));
        setNodeProperty(component, "hash", "hash2");
        assertEquals("hash2", getNodeProperty(component, "hash"));
        setNodeProperty(component, "url", "/url");
        assertEquals("/url", getNodeProperty(component, "url"));

        // Reset to default
        setNodeProperty(component, "number", "");
        assertEquals("1", getNodeProperty(component, "number"));
        setNodeProperty(component, "hash", "");
        assertEquals("hash", getNodeProperty(component, "hash"));
        setNodeProperty(component, "url", "");
        assertEquals("", getNodeProperty(component, "url"));

        // Validation
        assertNodePropertyStatus(component, "number", IStatus.OK, null);
        setNodeProperty(component, "number", "invalid");
        assertNodePropertyStatus(component, "number", IStatus.ERROR, null);

        assertNodePropertyStatus(component, "url", IStatus.OK, null);
        setNodeProperty(component, "url", "invalid");
        assertNodePropertyStatus(component, "url", IStatus.ERROR, null);
    }

    @Test
    public void testLoad() throws Exception {
        getPresenter().onLoad("go", ((IFile)getContentRoot().findMember("/game_object/props.go")).getContents());

        GameObjectNode gameObject = (GameObjectNode)getModel().getRoot();
        RefComponentNode component = (RefComponentNode)gameObject.getChildren().get(0);

        assertEquals("2", getNodeProperty(component, "number"));
        assertEquals("hash2", getNodeProperty(component, "hash"));
        assertEquals("/url", getNodeProperty(component, "url"));
    }

    @Test
    public void testSave() throws Exception {
        IFile goFile = (IFile)getContentRoot().findMember("/game_object/props.go");
        getPresenter().onLoad("go", goFile.getContents());

        GameObjectNode gameObject = (GameObjectNode)getModel().getRoot();
        RefComponentNode component = (RefComponentNode)gameObject.getChildren().get(0);

        setNodeProperty(component, "number", "3");
        setNodeProperty(component, "hash", "hash3");
        setNodeProperty(component, "url", "/url2");

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        getPresenter().onSave(stream, null);
        goFile.setContents(new ByteArrayInputStream(stream.toByteArray()), false, true, null);

        getPresenter().onLoad("go", goFile.getContents());
        gameObject = (GameObjectNode)getModel().getRoot();
        component = (RefComponentNode)gameObject.getChildren().get(0);

        assertEquals("3", getNodeProperty(component, "number"));
        assertEquals("hash3", getNodeProperty(component, "hash"));
        assertEquals("/url2", getNodeProperty(component, "url"));
    }

    @Test(expected = RuntimeException.class)
    public void testReload() throws Exception {
        GameObjectNode gameObject = (GameObjectNode)getModel().getRoot();
        RefComponentNode component = (RefComponentNode)gameObject.getChildren().get(0);

        // Default value
        assertEquals("1", getNodeProperty(component, "number"));

        saveScript("/script/props.script", "go.property(\"number2\", 0)");

        getNodeProperty(component, "number");
    }
}
