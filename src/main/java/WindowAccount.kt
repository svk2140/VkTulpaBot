import com.vk.api.sdk.client.actors.UserActor
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.net.CookieHandler
import java.net.CookieManager

class WindowAccount
{
    val stage = Stage()
    lateinit var host: CheckBox
    lateinit var pattern: TextField

    constructor()
    {
        stage.initStyle(StageStyle.UTILITY)
        stage.isAlwaysOnTop = true
        val scene = Scene(createMainPage(), 200.0, 100.0)
        stage.scene = scene
        stage.show()
    }

    fun createMainPage(): BorderPane
    {
        val content = VBox()

        content.children.add(TextField().also {
            it.text = "%message%"
            pattern = it
        })

        content.children.add(CheckBox().also {
            it.text = "Host"
            host = it
        })

        content.children.add(Button().also {
            it.text = "confirmAccount"
            it.prefWidth = 300.0

            it.onAction = EventHandler {
                if(host.isSelected && Main.INSTANCE.settings.any { it.host })
                {
                    Alert(Alert.AlertType.ERROR).also {
                        it.contentText = "Host already exists"
                    }.showAndWait()
                }
                else windowResponse()
            }
        })

        return BorderPane(content)
    }

    fun windowResponse()
    {
        val webView = WebView()
        webView.engine.load("https://oauth.vk.com/authorize?client_id=6839755&display=page&redirect_uri=https://api.vk.com/blank.html&scope=messages,offline&response_type=token&v=5.92")

        val stage = Stage()
        stage.scene = Scene(webView)
        stage.isAlwaysOnTop = true
        stage.show()

        webView.engine.locationProperty().addListener { observable, oldValue, newValue ->
            if (newValue.startsWith("https://api.vk.com/blank.html"))
            {
                create(newValue)
                stage.close()
                CookieManager.setDefault(CookieManager())
            }
        }
    }

    fun create(accResponse: String)
    {
        val token = accResponse.split("access_token=").last().split("&").first()
        val id = accResponse.split("user_id=").last().split("&").first().toInt()
        Main.INSTANCE.addAccount(Setting(UserActor(id, token), host.isSelected, pattern.text))
        stage.close()
    }
}