package com.asistente.personal

import android.Manifest
import android.app.*
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val prefs by lazy { getSharedPreferences("asistente", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var entry: EditText
    private lateinit var title: TextView
    private var tts: TextToSpeech? = null
    private var voiceReady = false
    private var awaiting: Pair<String, () -> Unit>? = null
    private var torchOn = false
    private val worker = Executors.newSingleThreadExecutor()
    private val speechRequest = 22
    private val bg get() = if (prefs.getBoolean("dark", true)) Color.rgb(20,20,22) else Color.WHITE
    private val fg get() = if (prefs.getBoolean("dark", true)) Color.WHITE else Color.BLACK
    private val secondary get() = if (prefs.getBoolean("dark", true)) Color.rgb(46,46,50) else Color.rgb(237,237,240)
    private val name get() = prefs.getString("name", "Asistente")!!.ifBlank { "Asistente" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (!prefs.getBoolean("welcome", false)) setup() else showChat()
    }
    override fun onInit(status: Int) { voiceReady = status == TextToSpeech.SUCCESS; if (voiceReady) tts?.language = Locale("es", "PE") }
    private fun base(header: String): LinearLayout {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 28, 18, 14); setBackgroundColor(bg) }
        setContentView(root)
        title = TextView(this).apply { text = header; textSize = 24f; setTextColor(fg); setPadding(0, 0, 0, 16) }
        root.addView(title)
        return root
    }
    private fun txt(s: String, size: Float = 15f): TextView = TextView(this).apply { text=s; textSize=size; setTextColor(fg); setPadding(8, 10, 8, 10) }
    private fun field(hint: String, value: String=""): EditText = EditText(this).apply { this.hint=hint; setText(value); setTextColor(fg); setHintTextColor(Color.GRAY); isSingleLine=true }
    private fun button(text: String, run: () -> Unit): Button = Button(this).apply { this.text=text; setOnClickListener { run() } }
    private fun setup() {
        base("Bienvenido a Asistente")
        root.addView(txt("Elige cómo quieres llamar a tu asistente. Podrás cambiarlo después. Tu información permanece en el celular salvo cuando elijas consultar Gemini."))
        val n=field("Nombre de tu asistente", name); root.addView(n)
        val own=field("Cómo quieres que te llame (opcional)"); root.addView(own)
        root.addView(txt("El historial se guardará automáticamente en almacenamiento privado local. Puedes exportar una copia a cualquier carpeta o microSD desde Ajustes. No se instalará una IA local con este prototipo."))
        root.addView(button("Comenzar") { prefs.edit().putString("name", n.text.toString().trim().ifEmpty { "Asistente" }).putString("owner",own.text.toString().trim()).putBoolean("welcome",true).apply(); showChat() })
    }
    private fun showChat() {
        base(name)
        val menu=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        menu.addView(button("Ajustes") { settings() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        menu.addView(button("Recuerdos") { memories() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        menu.addView(button("Nuevo chat") { confirm("¿Crear un chat nuevo? El historial anterior seguirá guardado.") { prefs.edit().putString("chat", UUID.randomUUID().toString()).apply(); showChat() } },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        root.addView(menu)
        val sc=ScrollView(this)
        messages=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        sc.addView(messages)
        root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        entry=field("Escribe tu mensaje…")
        row.addView(entry,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button("🎙") { microphone() })
        row.addView(button("Enviar") { val s=entry.text.toString().trim(); if(s.isNotEmpty()){ entry.setText(""); send(s) } })
        root.addView(row)
        renderHistory()
    }
    private fun chats(): JSONArray = try { JSONArray(prefs.getString("history","[]")) } catch (_:Exception){ JSONArray() }
    private fun activeChat() = prefs.getString("chat", "principal") ?: "principal"
    private fun renderHistory() {
        messages.removeAllViews()
        val all=chats()
        for(i in 0 until all.length()) { val m=all.optJSONObject(i) ?:continue; if(m.optString("chat")==activeChat()) bubble(m.optString("role"),m.optString("text")) }
    }
    private fun bubble(role: String, s: String) { messages.addView(txt((if(role=="user") "Tú" else name)+"\n"+s).apply { setBackgroundColor(secondary); setPadding(12,12,12,12) },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=8 }) }
    private fun store(role: String, s: String) { val a=chats(); a.put(JSONObject().put("chat",activeChat()).put("role",role).put("text",s).put("at",System.currentTimeMillis())); prefs.edit().putString("history",a.toString()).apply(); bubble(role,s) }
    private fun reply(s: String) { store("assistant",s); if(prefs.getBoolean("speak",false) && voiceReady) tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"answer") }
    private fun send(s: String) {
        store("user",s)
        val pending=awaiting
        if(pending != null) {
            val yes=s.lowercase(Locale.ROOT).trim() in listOf("sí","si","confirmo","acepto","sí, confirmo","si, confirmo")
            val no=s.lowercase(Locale.ROOT).trim() in listOf("no","cancelar","rechazo")
            if (yes){ awaiting=null; pending.second(); return }
            if (no){ awaiting=null; reply("Cancelado. No hice cambios."); return }
            reply("Tengo una confirmación pendiente: ${pending.first}. Responde 'sí' o 'no'.")
            return
        }
        val low=s.lowercase(Locale.ROOT)
        when {
            low.contains("linterna") && (low.contains("enciende") || low.contains("prende") || low.contains("apaga")) -> confirm("¿${if(low.contains("apaga")) "Apagar" else "Encender"} la linterna?") { setTorch(!low.contains("apaga")) }
            low.startsWith("recuerda ") || low.startsWith("guarda ") -> {
                val content=s.substringAfter(' ').trim()
                confirm("¿Guardar este recuerdo de forma permanente: '$content'?") { val arr=notes();arr.put(JSONObject().put("text",content).put("at",System.currentTimeMillis()));prefs.edit().putString("notes",arr.toString()).apply();reply("Recuerdo guardado en este dispositivo.") }
            }
            low.contains("qué recuerdas") || low.contains("mis recuerdos") -> { val arr=notes();reply(if(arr.length()==0) "Todavía no hay recuerdos aprobados." else (0 until arr.length()).joinToString("\n") { "• ${arr.optJSONObject(it)?.optString("text")}" }) }
            low.contains("hora") || low.contains("fecha") -> reply(SimpleDateFormat("EEEE d 'de' MMMM, HH:mm",Locale("es","PE")).format(Date()))
            low.contains("abre ") -> {
                val app=s.substringAfter("abre ","").trim()
                confirm("¿Intentar abrir '$app'?") { openApp(app) }
            }
            low.contains("temporizador") -> reply("La programación de temporizadores por lenguaje natural aún no está implementada. No he creado ninguno.")
            prefs.getString("gemini_key", "")!!.isNotBlank() -> askGemini(s)
            else -> reply("Puedo guardar recuerdos con tu permiso, consultar hora/fecha, controlar la linterna y abrir algunas apps. Para preguntas generales, configura una clave de Gemini en Ajustes. El asistente offline avanzado aún no está instalado.")
        }
    }
    private fun confirm(q: String, action: () -> Unit) {
        awaiting=q to action
        AlertDialog.Builder(this).setTitle("Confirmación").setMessage(q).setPositiveButton("Confirmar") { _,_-> awaiting=null;action() }.setNegativeButton("Cancelar") { _,_->awaiting=null;reply("Cancelado. No hice cambios.") }.show()
    }
    private fun notes(): JSONArray = try{JSONArray(prefs.getString("notes","[]"))}catch(_:Exception){JSONArray()}
    private fun memories() {
        base("Recuerdos aprobados")
        val a=notes()
        if(a.length()==0)root.addView(txt("No hay recuerdos todavía."))
        for(i in 0 until a.length()) {
            val index=i;val n=a.optJSONObject(i) ?: continue
            root.addView(txt(n.optString("text")))
            root.addView(button("Enviar a papelera") { confirm("¿Enviar este recuerdo a la papelera?") { val current=notes();val deleted=current.optJSONObject(index)?:return@confirm; val trash=try{JSONArray(prefs.getString("trash","[]"))}catch(_:Exception){JSONArray()};trash.put(deleted);val new=JSONArray();for(j in 0 until current.length())if(j!=index)new.put(current.get(j));prefs.edit().putString("notes",new.toString()).putString("trash",trash.toString()).apply();memories() } })
        }
        root.addView(button("Ver papelera") { trash() });root.addView(button("Volver") { showChat() })
    }
    private fun trash() {
        base("Papelera")
        val a=try{JSONArray(prefs.getString("trash","[]"))}catch(_:Exception){JSONArray()}
        for(i in 0 until a.length()) {
            val index=i;root.addView(txt(a.optJSONObject(i)?.optString("text")?:""))
            root.addView(button("Restaurar") { confirm("¿Restaurar este recuerdo?") { val trash=try{JSONArray(prefs.getString("trash","[]"))}catch(_:Exception){JSONArray()};val n=notes();n.put(trash.get(index));val remaining=JSONArray();for(j in 0 until trash.length())if(j!=index)remaining.put(trash.get(j));prefs.edit().putString("notes",n.toString()).putString("trash",remaining.toString()).apply();trash() } })
        }
        root.addView(button("Volver") { memories() })
    }
    private fun settings() {
        base("Configuración")
        root.addView(txt("Nombre, aspecto, voz y conexión"))
        val n=field("Nombre",name);root.addView(n)
        val dark=CheckBox(this).apply{text="Modo oscuro";isChecked=prefs.getBoolean("dark",true);setTextColor(fg)};root.addView(dark)
        val speak=CheckBox(this).apply{text="Leer las respuestas en voz alta";isChecked=prefs.getBoolean("speak",false);setTextColor(fg)};root.addView(speak)
        val key=field("Clave Gemini (opcional)",prefs.getString("gemini_key", "") ?: "");root.addView(key)
        root.addView(txt("La clave se guarda en preferencias privadas de la app; esta versión NO incluye cifrado adicional. Evita guardar información delicada aquí. Gemini requiere internet y su disponibilidad/cuotas dependen de Google."))
        root.addView(button("Guardar ajustes") { confirm("¿Guardar la nueva configuración?") {prefs.edit().putString("name",n.text.toString().trim().ifEmpty{"Asistente"}).putBoolean("dark",dark.isChecked).putBoolean("speak",speak.isChecked).putString("gemini_key",key.text.toString().trim()).apply();showChat()} })
        root.addView(button("Exportar datos JSON") { exportData() })
        root.addView(button("Importar datos JSON") { importData() })
        root.addView(button("Volver") { showChat() })
    }
    private fun exportData() {
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"asistente-respaldo.json") }
        startActivityForResult(intent,31)
    }
    private fun importData() {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="application/json"},32)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK)return
        if(requestCode==speechRequest){val text=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull();if(!text.isNullOrBlank()) { if(::entry.isInitialized) entry.setText(text); if(::entry.isInitialized) send(text) };return}
        val uri=data?.data?:return
        if(requestCode==31){try{val json=JSONObject().put("version",1).put("name",name).put("history",chats()).put("notes",notes()).put("trash",JSONArray(prefs.getString("trash","[]"))).put("dark",prefs.getBoolean("dark",true));contentResolver.openOutputStream(uri)?.use{it.write(json.toString(2).toByteArray(Charsets.UTF_8))};Toast.makeText(this,"Archivo exportado. No está cifrado: protégelo.",Toast.LENGTH_LONG).show()}catch(e:Exception){Toast.makeText(this,"Falló exportación: ${e.message}",Toast.LENGTH_LONG).show()}}
        if(requestCode==32) {confirm("¿IMPORTAR y reemplazar el historial y recuerdos actuales? Guarda una copia antes. El respaldo debe ser de Asistente."){try{val content=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:return@confirm;val obj=JSONObject(content);if(obj.optInt("version")!=1)throw IllegalArgumentException("Formato incompatible"); val history=obj.getJSONArray("history");val notes=obj.getJSONArray("notes");val trash=obj.optJSONArray("trash")?:JSONArray();prefs.edit().putString("history",history.toString()).putString("notes",notes.toString()).putString("trash",trash.toString()).putString("name",obj.optString("name","Asistente")).apply();showChat();reply("Importación terminada.")}catch(e:Exception){Toast.makeText(this,"Error al importar: ${e.message}",Toast.LENGTH_LONG).show()}}}
    }
    private fun microphone() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),3);return}
        try{startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-PE");putExtra(RecognizerIntent.EXTRA_PROMPT,"Habla con $name")},speechRequest)}catch(_:Exception){reply("No hay reconocimiento de voz disponible en el teléfono.")}
    }
    private fun setTorch(on:Boolean){try{val cm=getSystemService(CAMERA_SERVICE) as CameraManager;val id=cm.cameraIdList.firstOrNull{cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}?:throw Exception("Sin flash");cm.setTorchMode(id,on);torchOn=on;reply(if(on)"Linterna encendida." else "Linterna apagada.")}catch(e:Exception){reply("No pude controlar la linterna: ${e.message}")}}
    private fun openApp(query:String){val pm=packageManager;val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);val apps=pm.queryIntentActivities(intent,0);val a=apps.firstOrNull{it.loadLabel(pm).toString().contains(query,true)};if(a==null){reply("No encontré esa aplicación. Android puede limitar la lista visible.");return};try{startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.activityInfo.packageName,a.activityInfo.name));reply("Abriendo ${a.loadLabel(pm)}.")}catch(e:Exception){reply("No pude abrirla: ${e.message}")}}
    private fun askGemini(question:String){ reply("Consultando Gemini…")
        val key=prefs.getString("gemini_key", "")?:""
        worker.execute { try {
            val url=URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            val con=url.openConnection() as HttpURLConnection
            con.requestMethod="POST";con.connectTimeout=12000;con.readTimeout=45000;con.doOutput=true;con.setRequestProperty("Content-Type","application/json");con.setRequestProperty("x-goog-api-key",key)
            val payload=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text", "Eres un asistente personal llamado $name. Responde en español, de manera útil y concisa. No afirmes haber ejecutado acciones del teléfono. Pregunta antes de sugerir acciones. Pregunta: $question")))))
            con.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
            val code=con.responseCode;val body=(if(code in 200..299) con.inputStream else con.errorStream).bufferedReader().use{it.readText()}
            val answer=if(code in 200..299){val parts=JSONObject(body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");(0 until parts.length()).joinToString("\n"){parts.getJSONObject(it).optString("text")}.ifBlank{"No hubo respuesta."}}else "Gemini devolvió un error ($code). Revisa la clave, cuota o conexión."
            runOnUiThread{reply(answer)}
        }catch(e:Exception){runOnUiThread{reply("No se pudo consultar Gemini. Revisa internet y la clave API. ${e.javaClass.simpleName}")}} }
    }
    override fun onDestroy(){tts?.stop();tts?.shutdown();worker.shutdown();super.onDestroy()}
}
