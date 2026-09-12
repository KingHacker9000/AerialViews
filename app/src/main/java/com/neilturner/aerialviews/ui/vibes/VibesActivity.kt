package com.neilturner.aerialviews.ui.vibes

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.neilturner.aerialviews.ui.MainActivity
import java.util.Locale

/**
 * Projector-first launcher for local ambient video loops.
 *
 * The user selects a folder once through Android's Storage Access Framework. Direct child
 * video files are then presented as large, D-pad/touch friendly buttons and can be played
 * indefinitely in [VibePlayerActivity]. Keeping the media outside the APK makes it practical
 * to add large AI-generated vibe packs without rebuilding the app.
 */
class VibesActivity : AppCompatActivity() {
    private lateinit var selectFolderButton: Button
    private lateinit var randomButton: Button
    private lateinit var statusView: TextView
    private lateinit var videoContainer: LinearLayout
    private var videos: List<VibeVideo> = emptyList()

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                persistFolder(uri)
                refreshVideos()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(buildContentView())
        refreshVideos()
    }

    private fun buildContentView(): View {
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(8, 11, 18))
                setPadding(dp(32), dp(24), dp(32), dp(24))
            }

        root.addView(
            TextView(this).apply {
                text = "VIBES"
                textSize = 38f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        root.addView(
            TextView(this).apply {
                text = "Turn the projector into a window somewhere else."
                textSize = 17f
                setTextColor(Color.rgb(180, 190, 205))
                setPadding(0, dp(2), 0, dp(18))
            },
        )

        val controls =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        selectFolderButton = controlButton("Choose Vibes folder") { folderPicker.launch(savedFolderUri()) }
        randomButton = controlButton("Play random") {
            videos.randomOrNull()?.let(::playVideo)
        }
        val advancedButton = controlButton("Advanced settings") {
            startActivity(Intent(this, MainActivity::class.java))
        }

        controls.addView(selectFolderButton, weightedControlParams())
        controls.addView(randomButton, weightedControlParams())
        controls.addView(advancedButton, weightedControlParams())
        root.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        statusView =
            TextView(this).apply {
                textSize = 15f
                setTextColor(Color.rgb(160, 170, 185))
                setPadding(dp(4), dp(14), dp(4), dp(10))
            }
        root.addView(statusView)

        videoContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                addView(
                    videoContainer,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        return root
    }

    private fun refreshVideos() {
        videoContainer.removeAllViews()
        val folder = savedFolderUri()
        if (folder == null) {
            videos = emptyList()
            randomButton.isEnabled = false
            statusView.text = "Choose a folder containing your processed .mp4 vibe loops."
            addEmptyMessage("No Vibes folder selected yet.")
            selectFolderButton.requestFocus()
            return
        }

        videos = runCatching { readVideos(folder) }.getOrElse { emptyList() }
        randomButton.isEnabled = videos.isNotEmpty()

        if (videos.isEmpty()) {
            statusView.text = "No video files found in the selected folder."
            addEmptyMessage("Add .mp4, .mkv, .webm, or .mov files, then reopen Vibes.")
            selectFolderButton.requestFocus()
            return
        }

        statusView.text = "${videos.size} vibe${if (videos.size == 1) "" else "s"} ready • loops forever until you press Back"
        videos.forEach { video ->
            val button =
                Button(this).apply {
                    text = video.title
                    textSize = 20f
                    isAllCaps = false
                    minHeight = dp(76)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(22), dp(12), dp(22), dp(12))
                    setOnClickListener { playVideo(video) }
                }
            videoContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(10)
                },
            )
        }
        videoContainer.getChildAt(0)?.requestFocus()
    }

    private fun readVideos(treeUri: Uri): List<VibeVideo> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        val found = mutableListOf<VibeVideo>()

        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mimeType = cursor.getString(mimeIndex).orEmpty()
                if (!isVideo(displayName, mimeType)) continue

                found +=
                    VibeVideo(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        title = friendlyName(displayName),
                    )
            }
        }

        return found.sortedBy { it.title.lowercase(Locale.US) }
    }

    private fun isVideo(
        displayName: String,
        mimeType: String,
    ): Boolean {
        if (mimeType.startsWith("video/")) return true
        val lower = displayName.lowercase(Locale.US)
        return lower.endsWith(".mp4") ||
            lower.endsWith(".mkv") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".mov")
    }

    private fun friendlyName(fileName: String): String {
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        val withoutPrefix = withoutExtension.replace(Regex("^\\d+[ ._-]*"), "")
        return withoutPrefix
            .replace(Regex("[_-]+"), " ")
            .trim()
            .ifBlank { withoutExtension }
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase(Locale.US) else first.toString()
                }
            }
    }

    private fun playVideo(video: VibeVideo) {
        startActivity(
            Intent(this, VibePlayerActivity::class.java).apply {
                data = video.uri
                putExtra(VibePlayerActivity.EXTRA_TITLE, video.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    private fun persistFolder(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .apply()
    }

    private fun savedFolderUri(): Uri? =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)
            ?.let(Uri::parse)

    private fun addEmptyMessage(message: String) {
        videoContainer.addView(
            TextView(this).apply {
                text = message
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(185, 195, 210))
                setPadding(dp(16), dp(56), dp(16), dp(24))
            },
        )
    }

    private fun controlButton(
        label: String,
        action: () -> Unit,
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(60)
        setOnClickListener { action() }
    }

    private fun weightedControlParams() =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class VibeVideo(
        val uri: Uri,
        val title: String,
    )

    private companion object {
        const val PREFS_NAME = "vibes"
        const val KEY_FOLDER_URI = "folder_uri"
    }
}
