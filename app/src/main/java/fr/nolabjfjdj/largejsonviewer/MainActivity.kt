package fr.nolabjfjdj.largejsonviewer

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val jsonFactory = JsonFactory()
    private var selectedUri: Uri? = null
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var searchInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var openButton: Button
    private lateinit var searchButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF10151D.toInt())
        }

        val title = TextView(this).apply {
            text = "Large JSON Viewer"
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFF3F6FC.toInt())
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Lecteur en flux pour les gros fichiers JSON. Le fichier n'est pas chargé en entier en mémoire."
            textSize = 14f
            setTextColor(0xFFB6C2D2.toInt())
            setPadding(0, 8, 0, 16)
        }
        root.addView(subtitle, matchWrap())

        openButton = Button(this).apply {
            text = "Ouvrir un fichier JSON"
            setOnClickListener { openFilePicker() }
        }
        root.addView(openButton, matchWrap())

        status = TextView(this).apply {
            text = "Aucun fichier sélectionné."
            textSize = 14f
            setTextColor(0xFFE0E7F0.toInt())
            setPadding(0, 12, 0, 8)
        }
        root.addView(status, matchWrap())

        searchInput = EditText(this).apply {
            hint = "Rechercher un nom de champ ou une valeur"
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA8BA.toInt())
            setPadding(12, 8, 12, 8)
        }
        root.addView(searchInput, matchWrap())

        searchButton = Button(this).apply {
            text = "Rechercher dans le fichier"
            isEnabled = false
            setOnClickListener { startSearch() }
        }
        root.addView(searchButton, matchWrap())

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = 8; bottomMargin = 8 })

        val resultScroll = ScrollView(this)
        results = TextView(this).apply {
            text = "Les résultats apparaîtront ici."
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFE0E7F0.toInt())
            setTextIsSelectable(true)
            setPadding(12, 12, 12, 24)
        }
        resultScroll.addView(results)
        root.addView(resultScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = 8 })

        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "application/octet-stream", "*/*"))
        }
        startActivityForResult(intent, REQUEST_OPEN_FILE)
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "JSON"
            status.text = "Fichier : $name\nPrêt à lancer une recherche."
            searchButton.isEnabled = true
            results.text = "Saisis une valeur ou un nom de champ, puis lance la recherche."
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun startSearch() {
        val uri = selectedUri
        val query = searchInput.text.toString().trim()
        if (uri == null) {
            Toast.makeText(this, "Choisis d'abord un fichier.", Toast.LENGTH_SHORT).show()
            return
        }
        if (query.isEmpty()) {
            Toast.makeText(this, "Entre une valeur à rechercher.", Toast.LENGTH_SHORT).show()
            return
        }

        openButton.isEnabled = false
        searchButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Recherche en cours… Un fichier de plusieurs Go peut prendre du temps."
        results.text = ""

        executor.execute {
            val outcome = try {
                searchJson(uri, query)
            } catch (e: Exception) {
                SearchOutcome(emptyList(), "Erreur : ${e.message ?: e.javaClass.simpleName}")
            }
            runOnUiThread {
                progress.visibility = View.GONE
                openButton.isEnabled = true
                searchButton.isEnabled = true
                status.text = outcome.status
                results.text = if (outcome.matches.isEmpty()) {
                    "Aucun résultat affichable.\n\n${outcome.extra}"
                } else {
                    outcome.matches.joinToString("\n\n────────────────────\n\n") + "\n\n${outcome.extra}"
                }
            }
        }
    }

    private data class Frame(
        val type: JsonToken,
        val path: String,
        var fieldName: String? = null,
        var arrayIndex: Long = 0
    )

    private data class SearchOutcome(val matches: List<String>, val extra: String, val status: String = "Recherche terminée.")

    private fun searchJson(uri: Uri, query: String): SearchOutcome {
        val needle = query.lowercase(Locale.ROOT)
        val found = ArrayList<String>()
        var scannedTokens = 0L
        var skippedLongValues = 0
        var reachedLimit = false
        val maxResults = 200
        val maxTextChars = 1_000_000

        val input: InputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Impossible d'ouvrir ce fichier.")

        input.use { stream ->
            jsonFactory.createParser(stream).use { parser ->
                val stack = ArrayList<Frame>()

                fun currentPath(): String {
                    if (stack.isEmpty()) return "$"
                    val parent = stack.last()
                    return when (parent.type) {
                        JsonToken.START_OBJECT -> {
                            val key = parent.fieldName
                            if (key == null) parent.path else parent.path + "[\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"
                        }
                        JsonToken.START_ARRAY -> parent.path + "[" + parent.arrayIndex + "]"
                        else -> parent.path
                    }
                }

                fun consumeParentChild() {
                    if (stack.isEmpty()) return
                    val parent = stack.last()
                    if (parent.type == JsonToken.START_ARRAY) parent.arrayIndex++
                    if (parent.type == JsonToken.START_OBJECT) parent.fieldName = null
                }

                while (true) {
                    val token = parser.nextToken() ?: break
                    scannedTokens++

                    when (token) {
                        JsonToken.FIELD_NAME -> {
                            val key = parser.currentName ?: ""
                            val parent = stack.lastOrNull()
                            if (parent != null) {
                                parent.fieldName = key
                                if (key.lowercase(Locale.ROOT).contains(needle)) {
                                    val path = parent.path + "[\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"
                                    found.add("Chemin : $path\nChamp : $key")
                                }
                            }
                        }
                        JsonToken.START_OBJECT, JsonToken.START_ARRAY -> {
                            val path = currentPath()
                            consumeParentChild()
                            stack.add(Frame(token, path))
                        }
                        JsonToken.END_OBJECT, JsonToken.END_ARRAY -> {
                            if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                        }
                        else -> {
                            val path = currentPath()
                            val length = parser.textLength
                            if (length > maxTextChars) {
                                skippedLongValues++
                            } else {
                                val value = parser.valueAsString ?: parser.text ?: ""
                                if (value.lowercase(Locale.ROOT).contains(needle)) {
                                    found.add("Chemin : $path\nValeur : ${value.take(700)}")
                                }
                            }
                            consumeParentChild()
                        }
                    }

                    if (found.size >= maxResults) {
                        reachedLimit = true
                        break
                    }
                }
            }
        }

        val extra = buildString {
            append("Résultats affichés : ").append(found.size).append(" maximum ").append(maxResults).append(".")
            append("\nRecherche en flux : le fichier complet n'est pas chargé en mémoire.")
            if (reachedLimit) append("\nLimite atteinte : relance avec une requête plus précise.")
            if (skippedLongValues > 0) append("\nValeurs très longues ignorées pour protéger la mémoire : ").append(skippedLongValues).append(".")
            append("\nTokens parcourus : ").append(scannedTokens)
        }
        return SearchOutcome(found, extra, if (reachedLimit) "Recherche arrêtée à la limite de résultats." else "Recherche terminée.")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_OPEN_FILE = 41
    }
}
