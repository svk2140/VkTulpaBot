import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.scenario.Settings
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.stage.WindowEvent
import java.awt.AWTException
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO

fun main(args: Array<String>)
{
    Application.launch(Main::class.java, *args)
}

class Main : Application()
{
    companion object{lateinit var INSTANCE: Main}

    val path = Paths.get("save.json")

    val settings = FXCollections.observableArrayList<Setting>()
    val threads = ArrayList<Thread>()
    lateinit var selectSettings: Setting

    lateinit var trayIcon: TrayIcon
    var hiddenX: Double = 0.0
    var hiddenY: Double = 0.0
    var trayed: Boolean = false

    lateinit var vk: VkApiClient
    lateinit var jsonParser: JsonParser

    override fun start(stage: Stage)
    {
        INSTANCE = this

        val tr = HttpTransportClient()
        vk = VkApiClient(tr)
        jsonParser = JsonParser()

        initTray(stage)
        WindowMain(stage)

        load()
    }

    fun startBot(): Boolean
    {
        if(settings.size < 2)
        {
            Alert(Alert.AlertType.ERROR).also {
                it.contentText = "Need two or more accounts"
            }.showAndWait()

            return false
        }
        else if(settings.any { it.host })
        {
            val hostAcc = settings.find { it.host }!!
            val hostThread = HostThread(hostAcc.actor, hostAcc.pattern)

            threads.add(hostThread)

            for (setting in settings)
            {
                if(!setting.host)
                {
                    threads.add(TulpaThread(hostThread, setting.actor, setting.pattern))
                }
            }

            threads.forEach { it.start() }
            WindowMain.buttonStart.text = "Stop"

            return true
        }
        else
        {
            Alert(Alert.AlertType.ERROR).also {
                it.contentText = "Need one host account"
            }.showAndWait()

            return false
        }
    }

    fun stopBot()
    {
        threads.forEach { it.interrupt() }
        threads.clear()
    }

    override fun stop()
    {
        threads.forEach { it.interrupt() }
        super.stop()
    }

    fun load()
    {
        if(!Files.exists(path))
        {
            Files.createFile(path)
        }
        else
        {
            val json = JsonParser().parse(String(Files.readAllBytes(path))).asJsonArray

            for (setting in json)
            {
                val el = setting.asJsonObject
                val id = el.get("id").asInt
                val tk = el.get("tk").asString
                val pt = el.get("pt").asString
                val ih = el.get("ih").asBoolean
                val nm = el.get("nm").asString

                settings.add(Setting(UserActor(id, tk), ih, pt, nm))
            }

            if (settings.size >= 2 && settings.any { it.host }) startBot()
        }
    }

    fun save()
    {
        val json = JsonArray()

        for (setting in settings)
        {
            val el = JsonObject()
            el.addProperty("id", setting.actor.id)
            el.addProperty("tk", setting.actor.accessToken)
            el.addProperty("pt", setting.pattern)
            el.addProperty("ih", setting.host)
            el.addProperty("nm", setting.name)
            json.add(el)
        }

        Files.write(path, json.toString().toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
    }

    fun addAccount(setting: Setting)
    {
        settings.add(setting)
        save()
    }

    fun removeAccount(setting: Setting)
    {
        settings.remove(setting)
        save()
    }

    fun initTray(stage: Stage)
    {
        trayIcon = createIconTray(stage)

        Platform.setImplicitExit(false)

        stage.setOnCloseRequest(EventHandler<WindowEvent> { event ->
            event.consume()

            try
            {
                SystemTray.getSystemTray().add(trayIcon)
                trayed = true
                hiddenX = stage.getX()
                hiddenY = stage.getY()
                stage.hide()
            } catch (e: AWTException)
            {
                e.printStackTrace()
            }
        })
    }

    fun createIconTray(stage: Stage): TrayIcon
    {
        val trayIcon = TrayIcon(ImageIO.read(URL("http://icons.iconarchive.com/icons/scafer31000/bubble-circle-3/16/GameCenter-icon.png")))
        trayIcon.addActionListener {
            Platform.runLater {
                trayed = false
                stage.x = hiddenX
                stage.y = hiddenY
                stage.show()
                SystemTray.getSystemTray().remove(trayIcon)
            }
        }
        trayIcon.popupMenu = createTrayMenu()
        return trayIcon
    }

    fun createTrayMenu(): PopupMenu
    {
        val menu = PopupMenu()

        val exitItem = java.awt.MenuItem("Exit")
        exitItem.addActionListener {
            SystemTray.getSystemTray().remove(trayIcon)
            Platform.exit()
        }

        menu.add(exitItem)

        return menu
    }
}

data class Setting(val actor: UserActor, val host: Boolean, val pattern: String, val name: String = Main.INSTANCE.vk.users().get(actor).execute()[0].let {
    val name = "${it.firstName}  ${it.lastName}"
    if(host) "Host: " + name else name})
{
    override fun toString(): String
    {
        return name
    }
}

class HostThread(val actorHost: UserActor, pattern: String) : Thread()
{
    var prevMessageId: Int = -1

    override fun run()
    {
        val vk = Main.INSTANCE.vk
        val jsonParser = Main.INSTANCE.jsonParser

        val lps = vk.messages().getLongPollServer(actorHost).execute()
        var lastUpdate = lps.ts

        while (!isInterrupted)
        {
            val response = jsonParser.parse(vk.transportClient.get("https://${lps.server}?act=a_check&key=${lps.key}&ts=${lastUpdate}&wait=25&mode=2&version=3").content) as JsonObject
            lastUpdate = response.get("ts").asInt

            try
            {
                response.get("updates").asJsonArray.forEach {
                    val response = it.asJsonArray

                    if (response[0].asInt == 4)
                    {
                        val text = response[5].asString
                        val messageId = response[1].asInt
                        val data = response[6].asJsonObject

                        if (data.keySet().toList()[0] == "title" || data.get("from").asInt == actorHost.id)
                        {
                            prevMessageId = messageId
                        }
                    }
                }
            }
            catch (t: Throwable)
            {

            }
        }
    }

    fun confirmMessageIsTulpa()
    {
        Main.INSTANCE.vk.messages().delete(actorHost, prevMessageId).unsafeParam("delete_for_all", 1).execute()
    }
}

class TulpaThread(val hostThread: HostThread, val actorTulpa: UserActor, pattern: String) : Thread()
{
    val firstSym = pattern.split("%message%").first().toCharArray()
    val lastSym = pattern.split("%message%").last().toCharArray()

    override fun run()
    {
        val vk = Main.INSTANCE.vk
        val jsonParser = Main.INSTANCE.jsonParser
        
        val lps = vk.messages().getLongPollServer(actorTulpa).execute()
        var lastUpdate = lps.ts

        while (!isInterrupted)
        {
            val response = jsonParser.parse(vk.transportClient.get("https://${lps.server}?act=a_check&key=${lps.key}&ts=${lastUpdate}&wait=25&mode=2&version=3").content) as JsonObject
            lastUpdate = response.get("ts").asInt

            try
            {
                response.get("updates").asJsonArray.forEach {
                    val response = it.asJsonArray

                    if (response[0].asInt == 4)
                    {
                        val text = response[5].asString
                        val peerId = response[3].asInt
                        val data = response[6].asJsonObject

                        if (data.keySet().toList()[0] == "from" && data.get("from").asInt == hostThread.actorHost.id)
                        {
                            if (isTulpaMessage(text))
                            {
                                vk.messages().send(actorTulpa).peerId(peerId).message(text.substring(2, text.length-1)).execute()
                                hostThread.confirmMessageIsTulpa()
                            }
                        }
                    }
                }
            }
            catch (t: Throwable)
            {

            }
        }
    }

    fun isTulpaMessage(message: String): Boolean
    {
        firstSym.forEachIndexed { index, char ->
            if(message[index] != char) return false
        }

        lastSym.forEachIndexed { index, char ->
            if(message[message.length-lastSym.size+index] != char) return false
        }

        return true
    }
}