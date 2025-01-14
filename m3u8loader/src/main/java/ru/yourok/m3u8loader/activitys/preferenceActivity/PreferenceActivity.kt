package ru.yourok.m3u8loader.activitys.preferenceActivity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_preference.*
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.settings.Preferences
import ru.yourok.dwl.settings.Settings
import ru.yourok.dwl.storage.RequestStoragePermissionActivity
import ru.yourok.dwl.utils.Saver
import ru.yourok.m3u8loader.BuildConfig
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.theme.Theme


/**
 * Created by yourok on 18.11.17.
 */
class PreferenceActivity : AppCompatActivity() {
    private var lastTheme = true

    companion object {
        val Result = 1000
        var changTheme = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.set(this)
        setContentView(R.layout.activity_preference)
        lastTheme = Preferences.get("ThemeDark", true) as Boolean

        checkboxConvert.setOnCheckedChangeListener { _, b ->
            if (b) {
                if (!ConverterHelper.isSupport()) {
                    Toast.makeText(this, R.string.warn_install_convertor, Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<ImageButton>(R.id.imageButtonSearchDirectory).setOnClickListener {
            val intent = Intent(this, DirectoryActivity::class.java)
            intent.data = Uri.parse(editTextDirectoryPath.text.toString())
            startActivityForResult(intent, 1202)
        }

        try {
            val version = "YouROK " + getText(R.string.app_name) + " ${BuildConfig.FLAVOR} ${BuildConfig.VERSION_NAME}"
            (findViewById<TextView>(R.id.textViewVersion)).text = version
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.players_names))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerChoosePlayer.adapter = adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            findViewById<Button>(R.id.buttonRequestPermission).visibility = View.VISIBLE
            findViewById<Button>(R.id.buttonRequestPermission).setOnClickListener {
                startActivity(Intent(this, RequestStoragePermissionActivity::class.java))
            }
        }

        findViewById<Button>(R.id.buttonDefOptions).setOnClickListener {
            defSettings()
        }

        findViewById<Button>(R.id.buttonOk).setOnClickListener {
            saveSettings()
            finish()
        }

        findViewById<Button>(R.id.buttonCancel).setOnClickListener { finish() }

        if (savedInstanceState == null)
            loadSettings()
    }

    override fun onPause() {
        super.onPause()
        changTheme = Preferences.get("ThemeDark", true) as Boolean != lastTheme
    }

    private fun loadSettings() {
        try {
            findViewById<EditText>(R.id.editTextDirectoryPath)?.setText(Settings.downloadPath)
            findViewById<EditText>(R.id.editTextThreads)?.setText(Settings.threads.toString())
            findViewById<EditText>(R.id.editTextRepeatError)?.setText(Settings.errorRepeat.toString())
            findViewById<EditText>(R.id.editTextCookies)?.setText(Settings.headers["Cookie"])
            findViewById<EditText>(R.id.editTextUseragent)?.setText(Settings.headers["User-Agent"])
            findViewById<CheckBox>(R.id.checkboxConvert)?.setChecked(Settings.convertVideo)
            findViewById<CheckBox>(R.id.checkboxLoadItemsSize)?.setChecked(Settings.preloadSize)
            findViewById<CheckBox>(R.id.checkBoxChooseTheme)?.setChecked(Preferences.get("ThemeDark", true) as Boolean)
            findViewById<CheckBox>(R.id.checkBoxSimpleProgress)?.setChecked(Preferences.get("SimpleProgress", false) as Boolean)
            findViewById<Spinner>(R.id.spinnerChoosePlayer)?.setSelection(Preferences.get("Player", 0) as Int)
        } catch (e: Exception) {
            defSettings()
        }
    }

    private fun saveSettings() {
        Settings.downloadPath = editTextDirectoryPath.text.toString()
        Settings.threads = editTextThreads.text.toString().toInt()
        Settings.errorRepeat = editTextRepeatError.text.toString().toInt()
        Settings.headers["Cookie"] = editTextCookies.text.toString()
        Settings.headers["User-Agent"] = editTextUseragent.text.toString()
        Settings.convertVideo = checkboxConvert.isChecked
        Settings.preloadSize = checkboxLoadItemsSize.isChecked

        val ch = checkBoxChooseTheme.isChecked
        Preferences.set("ThemeDark", ch)

        val sp = checkBoxSimpleProgress.isChecked
        Preferences.set("SimpleProgress", sp)

        val sel = spinnerChoosePlayer.selectedItemPosition
        Preferences.set("Player", sel)

        Saver.saveSettings()
    }

    private fun defSettings() {
        findViewById<EditText>(R.id.editTextDirectoryPath)?.setText(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path)
        findViewById<EditText>(R.id.editTextThreads)?.setText("20")
        findViewById<EditText>(R.id.editTextRepeatError)?.setText("5")
        findViewById<EditText>(R.id.editTextCookies)?.setText("")
        findViewById<EditText>(R.id.editTextUseragent)?.setText("")
        findViewById<CheckBox>(R.id.checkboxConvert)?.setChecked(false)
        findViewById<CheckBox>(R.id.checkboxLoadItemsSize)?.setChecked(false)
        findViewById<CheckBox>(R.id.checkBoxChooseTheme)?.setChecked(true)
        findViewById<CheckBox>(R.id.checkBoxSimpleProgress)?.setChecked(false)
        findViewById<Spinner>(R.id.spinnerChoosePlayer)?.setSelection(0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1202 && data != null)
            editTextDirectoryPath.setText(data.getStringExtra("filename"))
    }
}