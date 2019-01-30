import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.*
import javafx.stage.Stage

class WindowMain
{
    companion object
    {
        lateinit var buttonStart: Button
    }

    constructor(stage: Stage)
    {
        val scene = Scene(createMainPage(), 400.0, 600.0)
        stage.setScene(scene)
        stage.resizableProperty().set(false)
        stage.show()
    }

    fun createMainPage(): VBox
    {
        val list = StackPane().also {
            it.children.add(createTaskList())
            it.setPrefSize(400.0, 550.0)
        }

        val button = BorderPane().also {
            val button = Button().also {button ->
                button.text = "Start"
                button.onAction = EventHandler {
                    if (button.text.equals("Start"))
                    {
                        if(Main.INSTANCE.startBot()) button.text = "Stop"
                    } else
                    {
                        button.text = "Start"
                        Main.INSTANCE.stopBot()
                    }
                }
            }

            buttonStart = button
            it.center = button
            it.setPrefSize(400.0, 50.0)
        }

        return VBox().also { it.children.addAll(list, button) }
    }

    fun createTaskList(): ListView<Setting>
    {
        val list = ListView<Setting>()

        val menuList = createTaskListContextMenu()
        val menuSelectTask = createTaskContextMenu(list)

        setFullAnchor(list)
        list.items = Main.INSTANCE.settings
        list.setCellFactory {
            object : ListCell<Setting>()
            {
                override fun updateItem(item: Setting?, empty: Boolean)
                {
                    super.updateItem(item, empty)

                    if (item != null)
                    {
                        text = item.toString()
                        contextMenu = menuSelectTask
                    }
                    else
                    {
                        text = null
                        contextMenu = menuList
                    }
                }
            }
        }

        list.setOnMouseClicked { event ->
            if (event.button == MouseButton.SECONDARY && list.getItems().isEmpty())
                menuList.show(list, event.screenX, event.screenY)

            if (event.button == MouseButton.PRIMARY)
            {
                list.getSelectionModel().getSelectedItem()?.also { Main.INSTANCE.selectSettings = it }
            }
        }

        return list
    }

    fun setFullAnchor(node: Node)
    {
        AnchorPane.setTopAnchor(node, 0.0)
        AnchorPane.setBottomAnchor(node, 0.0)
        AnchorPane.setLeftAnchor(node, 0.0)
        AnchorPane.setRightAnchor(node, 0.0)
    }

    fun createTaskListContextMenu(): ContextMenu
    {
        val addTaskButton = MenuItem("Add account").also { it.setOnAction { WindowAccount() } }
        return ContextMenu().also { it.items.add(addTaskButton) }
    }

    fun createTaskContextMenu(list: ListView<Setting>): ContextMenu
    {
        val addTaskButton = MenuItem("Add account").also { it.setOnAction {
            WindowAccount()
        } }

        val removeTaskButton = MenuItem("Remove this account").also { it.setOnAction {
            Main.INSTANCE.removeAccount(list.selectionModel.selectedItem)
        } }

        return ContextMenu().also { it.items.addAll(addTaskButton, removeTaskButton) }
    }
}