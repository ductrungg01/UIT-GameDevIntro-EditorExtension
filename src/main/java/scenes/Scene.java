package scenes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import components.Component;
import deserializers.ComponentDeserializer;
import deserializers.GameObjectDeserializer;
import deserializers.PrefabDeserializer;
import editor.*;
import editor.windows.OpenSceneWindow;
import editor.windows.SceneHierarchyWindow;
import org.joml.Vector2f;
import physics2d.Physics2D;
import renderer.Renderer;
import system.*;
import util.SceneUtils;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Scene {
    //region Fields
    final String LEVEL_PATH = "level.txt";
    final String PREFAB_PATH = "prefabs.txt";
    //region Fields
    private Renderer renderer;
    private Camera camera;
    private boolean isRunning;
    private List<GameObject> gameObjects;
    private List<GameObject> pendingObjects;
    private Physics2D physics2D;
    //endregion
    private SceneInitializer sceneInitializer;
    private MouseControls mouseControls = new MouseControls();
    private KeyControls keyControls = new KeyControls();
    //endregion

    //region Constructors
    public Scene(SceneInitializer sceneInitializer) {
        this.sceneInitializer = sceneInitializer;
        this.physics2D = new Physics2D();
        this.renderer = new Renderer();
        this.gameObjects = new ArrayList<>();
        this.pendingObjects = new ArrayList<>();
        this.isRunning = false;
    }
    //endregion

    //region Properties
    public Physics2D getPhysics() {
        return this.physics2D;
    }

    public List<GameObject> getGameObjects() {
        return this.gameObjects;
    }

    public GameObject getGameObject(int gameObjectId) {
        Optional<GameObject> result = this.gameObjects.stream()
                .filter(gameObject -> gameObject.getUid() == gameObjectId)
                .findFirst();

        return result.orElse(null);
    }

    public GameObject getGameObject(String gameObjectName) {
        Optional<GameObject> result = this.gameObjects.stream()
                .filter(gameObject -> gameObject.name.equals(gameObjectName))
                .findFirst();

        return result.orElse(null);
    }
    //endregion

    //region Methods
    public void init() {
        this.camera = new Camera(new Vector2f(-40, -Camera.screenSize.y - 30));
        this.camera.setDefaultZoom();
        this.sceneInitializer.loadResources(this);
        this.sceneInitializer.init(this);
    }

    /**
     * Start is called before the first frame update
     */
    public void start() {

        for (int i = 0; i < gameObjects.size(); i++) {
            GameObject go = gameObjects.get(i);
            go.start();
            this.renderer.add(go);
            this.physics2D.add(go);
        }
        isRunning = true;
    }

    public void addGameObjectToScene(GameObject go) {
        if (!isRunning) {
            gameObjects.add(go);
        } else {
            pendingObjects.add(go);
        }
    }

    public void removeAllGameObjectInScene() {
        this.gameObjects.clear();
    }

    public void destroy() {
        for (GameObject go : gameObjects) {
            go.destroy();
        }
    }

    public <T extends Component> GameObject findGameObjectWith(Class<T> clazz) {
        for (GameObject go : gameObjects) {
            if (go.getComponent(clazz) != null) {
                return go;
            }
        }

        return null;
    }

    public <T extends Component> List<GameObject> findAllGameObjectWith(Class<T> clazz) {
        List<GameObject> gameObjectList = new ArrayList<>();

        for (GameObject go : gameObjects) {
            if (go.getComponent(clazz) != null) {
                gameObjectList.add(go);
            }
        }

        return gameObjectList;
    }

    public List<GameObject> findAllGameObjectWithTag(String tag) {
        List<GameObject> gameObjectList = new ArrayList<>();

        for (GameObject go : this.gameObjects) {
            if (go.compareTag(tag)) {
                gameObjectList.add(go);
            }
        }

        return gameObjectList;
    }

    public void editorUpdate(float dt) {
        this.camera.adjustProjection();
        mouseControls.editorUpdate(dt);
        keyControls.editorUpdate(dt);

        for (int i = 0; i < gameObjects.size(); i++) {
            GameObject go = gameObjects.get(i);

            go.editorUpdate(dt);

            if (go.isDead()) {
                gameObjects.remove(i);
                this.renderer.destroyGameObject(go);
                this.physics2D.destroyGameObject(go);
                i--;
            }
        }

        for (GameObject go : pendingObjects) {
            gameObjects.add(go);
            go.start();
            this.renderer.add(go);
            this.physics2D.add(go);
        }
        pendingObjects.clear();
    }

    /**
     * Update is called once per frame
     *
     * @param dt : The interval in seconds from the last frame to the current one
     */
    public void update(float dt) {
        this.camera.adjustProjection();
        this.physics2D.update(dt);

        for (int i = 0; i < gameObjects.size(); i++) {
            GameObject go = gameObjects.get(i);
            go.update(dt);

            if (go.isDead()) {
                gameObjects.remove(i);
                this.renderer.destroyGameObject(go);
                this.physics2D.destroyGameObject(go);
                i--;
            }
        }

        for (GameObject go : pendingObjects) {
            gameObjects.add(go);
            go.start();
            this.renderer.add(go);
            this.physics2D.add(go);
        }
        pendingObjects.clear();
    }

    public void render() {
        this.renderer.render();
    }

    public Camera camera() {
        return this.camera;
    }

    public MouseControls getMouseControls() {
        return this.mouseControls;
    }

    public KeyControls getKeyControls() {
        return this.keyControls;
    }

    public void imgui() {
        this.sceneInitializer.imgui();
    }
    //endregion

    //region Save and Load
    public void save(boolean isShowMessage) {
        Window.getImguiLayer().getInspectorWindow().clearSelected();
        SceneHierarchyWindow.clearSelectedGameObject();
        if (SceneUtils.CURRENT_SCENE.isEmpty()) return;
        String level_path = "data\\" + SceneUtils.CURRENT_SCENE + "\\" + LEVEL_PATH;
        String prefab_path = "data\\" + PREFAB_PATH;

        //region Save Game Object
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Component.class, new ComponentDeserializer())
                .registerTypeAdapter(GameObject.class, new GameObjectDeserializer())
                .enableComplexMapKeySerialization()
                .create();

        try {
            FileWriter writer = new FileWriter(level_path);

            List<GameObject> objsToSerialize = new ArrayList<>();
            for (GameObject obj : this.gameObjects) {
                if (obj.doSerialization()) {
                    objsToSerialize.add(obj);
                }
            }

            writer.write(gson.toJson(objsToSerialize));
            writer.close();
            if (isShowMessage) {
                //MessageBox.setContext(true, MessageBox.TypeOfMsb.NORMAL_MESSAGE, "Save scene '" + SceneUtils.CURRENT_SCENE + "' successfully");
                Debug.Log("Save scene '" + SceneUtils.CURRENT_SCENE + "' successfully", LogType.Success);
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (isShowMessage) {
                //MessageBox.setContext(true, MessageBox.TypeOfMsb.NORMAL_MESSAGE, "Save failed");
                Debug.Log("Save scene '" + SceneUtils.CURRENT_SCENE + "' FAIL", LogType.Error);
            }
        }
        //endregion

        //region Save Prefabs
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Component.class, new ComponentDeserializer())
                .registerTypeAdapter(GameObject.class, new PrefabDeserializer())
                .enableComplexMapKeySerialization()
                .create();

        try {
            FileWriter writer = new FileWriter(prefab_path);

            List<GameObject> objsToSerialize = GameObject.PrefabLists;

            writer.write(gson.toJson(objsToSerialize));
            writer.close();
            if (isShowMessage) {
                //MessageBox.setContext(true, MessageBox.TypeOfMsb.NORMAL_MESSAGE, "Save scene '" + SceneUtils.CURRENT_SCENE + "' successfully");
                Debug.Log("Save prefab successfully", LogType.Success);
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (isShowMessage) {
                Debug.Log("Save prefab FAIL", LogType.Error);
                //MessageBox.setContext(true, MessageBox.TypeOfMsb.NORMAL_MESSAGE, "Save failed");
            }
        }
        //endregion

    }

    public void load() {
        if (SceneUtils.CURRENT_SCENE.isEmpty()) return;
        File folder = new File("data\\" + SceneUtils.CURRENT_SCENE);
        if (!folder.exists()) {
            JOptionPane.showMessageDialog(null, "Cannot find the previous scene (" + SceneUtils.CURRENT_SCENE + ")",
                    "ERROR", JOptionPane.ERROR_MESSAGE);
            Window.get().changeCurrentScene("", false, false);
            OpenSceneWindow.open(false);
            return;
        }

        String level_path = "data\\" + SceneUtils.CURRENT_SCENE + "\\" + LEVEL_PATH;
        String prefab_path = "data\\" + PREFAB_PATH;

        //region Load Game object
        int maxGoId = -1;
        int maxCompId = -1;
        GameObject.setCurrentMaxUid(0);

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Component.class, new ComponentDeserializer())
                .registerTypeAdapter(GameObject.class, new GameObjectDeserializer())
                .enableComplexMapKeySerialization()
                .create();

        String inFile = "";

        try {
            inFile = new String(Files.readAllBytes(Paths.get(level_path)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!inFile.equals("")) {
            GameObject[] objs = gson.fromJson(inFile, GameObject[].class);
            for (int i = 0; i < objs.length; i++) {
                GameObject go = objs[i];

                addGameObjectToScene(go);

                for (Component c : go.getAllComponents()) {
                    if (c.getUid() > maxCompId) {
                        maxCompId = c.getUid();
                    }
                }

                if (go.getUid() > maxGoId) {
                    maxGoId = go.getUid();
                }
            }

            maxGoId++;
            maxCompId++;

            GameObject.init(maxGoId);
            Component.init(maxCompId);
        }

        for (GameObject g : this.gameObjects) {
            g.refreshTexture();
        }
        //endregion

        //region Load Prefabs
        GameObject.PrefabLists.clear();

        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Component.class, new ComponentDeserializer())
                .registerTypeAdapter(GameObject.class, new PrefabDeserializer())
                .enableComplexMapKeySerialization()
                .create();

        inFile = "";

        try {
            inFile = new String(Files.readAllBytes(Paths.get(prefab_path)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!inFile.equals("")) {
            GameObject[] objs = gson.fromJson(inFile, GameObject[].class);
            for (int i = 0; i < objs.length; i++) {
                GameObject prefab = objs[i];

                GameObject.PrefabLists.add(prefab);

                for (Component c : prefab.getAllComponents()) {
                    if (c.getUid() > maxCompId) {
                        maxCompId = c.getUid();
                    }
                }

                prefab.refreshTexture();

                if (prefab.getUid() > maxGoId) {
                    maxGoId = prefab.getUid();
                }
            }

            maxGoId++;
            maxCompId++;

            GameObject.init(maxGoId);
            Component.init(maxCompId);
        }
        //endregion
    }
    //endregion
}
