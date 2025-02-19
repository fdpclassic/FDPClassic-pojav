/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.script

import jdk.internal.dynalink.beans.StaticClass
import jdk.nashorn.api.scripting.JSObject
import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import jdk.nashorn.api.scripting.ScriptUtils
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.script.api.*
import net.ccbluex.liquidbounce.script.api.global.Chat
import net.ccbluex.liquidbounce.script.api.global.Setting
import net.ccbluex.liquidbounce.utils.ClientUtils
import net.ccbluex.liquidbounce.utils.MinecraftInstance
import java.io.File
import java.util.function.Function
import javax.script.ScriptEngineManager

class Script(private val scriptFile: File) : MinecraftInstance() {

    private val scriptEngine = NashornScriptEngineFactory().getScriptEngine(emptyArray(), this.javaClass.classLoader, ScriptSafetyManager.classFilter)
    private val scriptText = scriptFile.readText(Charsets.UTF_8)

    // Script information
    lateinit var scriptName: String
    lateinit var scriptVersion: String
    lateinit var scriptAuthors: Array<String>

    private var state = false
    private val events = HashMap<String, JSObject>()
    private val registeredModules = mutableListOf<Module>()
    private val registeredCommands = mutableListOf<Command>()

    init {
        // Global classes
        scriptEngine.put("Chat", StaticClass.forClass(Chat::class.java))
        scriptEngine.put("Setting", StaticClass.forClass(Setting::class.java))

        // Global instances
        scriptEngine.put("mc", mc)
        scriptEngine.put("moduleManager", LiquidBounce.moduleManager)
        scriptEngine.put("commandManager", LiquidBounce.commandManager)
        scriptEngine.put("scriptManager", LiquidBounce.scriptManager)

        // Global functions
        scriptEngine.put("registerScript", RegisterScript())

        supportLegacyScripts()

        scriptEngine.eval(scriptText)

        callEvent("load")
    }

    @Suppress("UNCHECKED_CAST")
    inner class RegisterScript : Function<JSObject, Script> {
        /**
         * Global function 'registerScript' which is called to register a script.
         * @param scriptObject JavaScript object containing information about the script.
         * @return The instance of this script.
         */
        override fun apply(scriptObject: JSObject): Script {
            scriptName = scriptObject.getMember("name") as String
            scriptVersion = scriptObject.getMember("version") as String
            scriptAuthors = ScriptUtils.convert(scriptObject.getMember("authors"), Array<String>::class.java) as Array<String>

            return this@Script
        }
    }

    /**
     * Registers a new script module.
     * @param moduleObject JavaScript object containing information about the module.
     * @param callback JavaScript function to which the corresponding instance of [ScriptModule] is passed.
     * @see ScriptModule
     */
    @Suppress("unused")
    fun registerModule(moduleObject: JSObject, callback: JSObject) {
        val module = ScriptModule(moduleObject)
        LiquidBounce.moduleManager.registerModule(module)
        registeredModules += module
        callback.call(moduleObject, module)
    }

    /**
     * Registers a new script command.
     * @param commandObject JavaScript object containing information about the command.
     * @param callback JavaScript function to which the corresponding instance of [ScriptCommand] is passed.
     * @see ScriptCommand
     */
    @Suppress("unused")
    fun registerCommand(commandObject: JSObject, callback: JSObject) {
        val command = ScriptCommand(commandObject)
        LiquidBounce.commandManager.registerCommand(command)
        registeredCommands += command
        callback.call(commandObject, command)
    }

    fun supportLegacyScripts() {
        if (!scriptText.lines().first().contains("api_version=2")) {
            ClientUtils.logWarn("[ScriptAPI] Running script '${scriptFile.name}' with legacy support.")
            val legacyScript = LiquidBounce::class.java.getResource("/assets/minecraft/fdpclient/scriptapi/legacy.js").readText()
            scriptEngine.eval(legacyScript)
        }
    }

    /**
     * Called from inside the script to register a new event handler.
     * @param eventName Name of the event.
     * @param handler JavaScript function used to handle the event.
     */
    fun on(eventName: String, handler: JSObject) {
        events[eventName] = handler
    }

    /**
     * Called when the client enables the script.
     */
    fun onEnable() {
        if (state) return

        callEvent("enable")
        state = true
    }

    /**
     * Called when the client disables the script. Handles unregistering all modules and commands
     * created with this script.
     */
    fun onDisable() {
        if (!state) return

        registeredModules.forEach { LiquidBounce.moduleManager.unregisterModule(it) }
        registeredCommands.forEach { LiquidBounce.commandManager.unregisterCommand(it) }

        callEvent("disable")
        state = false
    }

    /**
     * Imports another JavaScript file inro the context of this script.
     * @param scriptFile Path to the file to be imported.
     */
    fun import(scriptFile: String) {
        scriptEngine.eval(File(LiquidBounce.scriptManager.scriptsFolder, scriptFile).readText())
    }

    /**
     * Calls the handler of a registered event.
     * @param eventName Name of the event to be called.
     */
    private fun callEvent(eventName: String) {
        try {
            events[eventName]?.call(null)
        } catch (throwable: Throwable) {
            ClientUtils.logError("[ScriptAPI] Exception in script '$scriptName'!", throwable)
        }
    }
}