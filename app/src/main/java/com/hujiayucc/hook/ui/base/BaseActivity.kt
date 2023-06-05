package com.hujiayucc.hook.ui.base

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.base.ModuleAppCompatActivity
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.service.SkipService
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.ui.adapter.ViewPagerAdapter
import com.hujiayucc.hook.ui.fragment.MainFragment
import com.hujiayucc.hook.utils.*
import com.hujiayucc.hook.utils.Data.checkRoot
import com.hujiayucc.hook.utils.Data.hideOrShowLauncherIcon
import com.hujiayucc.hook.utils.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.utils.Data.isLauncherIconShowing
import com.hujiayucc.hook.utils.Data.runService
import com.hujiayucc.hook.utils.Data.setSpan
import com.hujiayucc.hook.utils.Data.stopService
import com.hujiayucc.hook.utils.Data.updateConfig
import com.hujiayucc.hook.utils.FormatJson.formatJson
import org.json.JSONObject
import top.defaults.colorpicker.ColorPickerPopup
import java.io.File
import java.io.FileOutputStream
import java.util.*


open class BaseActivity: ModuleAppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private val fragmentList = ArrayList<MainFragment>()
    private lateinit var adapter: ViewPagerAdapter
    private lateinit var imageView: ImageView
    private var alertimageView: ImageView? = null
    private var localeID = 0
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        localeID = prefs().get(Data.localeId)
        if (localeID != 0) checkLanguage(Language.fromId(localeID))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        // 检查是否已经授权该权限
        if (checkSelfPermission(Manifest.permission.CHANGE_CONFIGURATION) != PackageManager.PERMISSION_GRANTED) {
            // 请求授权该权限
            requestPermissions(arrayOf(Manifest.permission.CHANGE_CONFIGURATION), 2001)
        }
        // 适配 Android13 通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2000)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun checkLanguage(language: Locale) {
        val configuration = resources.configuration
        configuration.setLocale(language)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val locale = resources.configuration.locale
        if (Language.fromId(localeID) != locale) recreate()
    }

    override fun recreate() {
        super.finish()
        val intent = Intent(applicationContext, this::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        applicationContext.startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun finish() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        excludeFromRecent(true)
    }

    override fun onResume() {
        super.onResume()
        excludeFromRecent(false)
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.menu_global).isChecked = prefs().get(Data.global)
        menu.findItem(R.id.menu_show_hook_success).isChecked = prefs().get(Data.hookTip)
        menu.findItem(R.id.menu_hide_icon).isChecked = isLauncherIconShowing.not()
        menu.findItem(R.id.menu_auto_skip).isChecked = isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME)

        val group = menu.findItem(R.id.menu_language_settings).subMenu?.item
        when (localeID) {
            0 -> group?.subMenu?.findItem(R.id.menu_language_defualt)?.isChecked = true
            1 -> group?.subMenu?.findItem(R.id.menu_language_en)?.isChecked = true
            2 -> group?.subMenu?.findItem(R.id.menu_language_zh)?.isChecked = true
        }
        menu.findItem(R.id.menu_language_settings).subMenu?.setHeaderTitle(getString(R.string.language_settings).setSpan(resources.getColor(
            R.color.theme)))
        menu.findItem(R.id.menu_theme_settings).subMenu?.setHeaderTitle(getString(R.string.menu_theme_settings).setSpan(resources.getColor(
            R.color.theme)))
        menu.findItem(R.id.menu_module_settings).subMenu?.setHeaderTitle(getString(R.string.menu_module_settings).setSpan(resources.getColor(
            R.color.theme)))
        this.menu = menu
        return true
    }

    override fun onStart() {
        super.onStart()
        val isChecked = isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME)
        menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
        if (isChecked) {
            intent = Intent(applicationContext, SkipService::class.java)
            startService(intent)
        }
    }

    /** 隐藏最近任务列表视图 */
    @Suppress("DEPRECATION")
    private fun excludeFromRecent(exclude: Boolean) {
        try {
            val manager: ActivityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (appTask in manager.appTasks) {
                if (appTask.taskInfo.id == taskId) {
                    appTask.setExcludeFromRecents(exclude)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkUpdate(show: Boolean) {
        Thread {
            val info = Update.checkUpdate()
            var hotFix = false
            if (info != null) {
                if (info.getInt("versionCode") > BuildConfig.VERSION_CODE) {
                    val url = Uri.parse(info.getString("url"))
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    runOnUiThread {
                        binding.mainStatus.text = getString(R.string.has_update)
                        binding.mainActiveStatus.setOnClickListener {
                            startActivity(intent)
                        }
                    }
                } else {
                    runOnUiThread {
                        if (show && !hotFix) Toast.makeText(applicationContext, getString(R.string.latest_version), Toast.LENGTH_SHORT).show()
                    }
                }
            } else runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.check_update_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    fun initView() {
        tabLayout = binding.tabLayout
        viewPager = binding.viewPager
        initBackGround()
        val userBundle = Bundle()
        userBundle.putBoolean("system", false)
        val user = Fragment.instantiate(applicationContext, MainFragment::class.java.name, userBundle) as MainFragment
        val systemBundle = Bundle()
        systemBundle.putBoolean("system", true)
        val system = Fragment.instantiate(applicationContext, MainFragment::class.java.name, systemBundle) as MainFragment
        fragmentList.add(user)
        fragmentList.add(system)
        tabLayout.setupWithViewPager(viewPager)
        val title = arrayOf(
            getString(R.string.user_app),
            getString(R.string.system_app)
        )
        adapter = ViewPagerAdapter(supportFragmentManager, fragmentList, title)
        viewPager.adapter = adapter
        viewPager.currentItem = 0

        if (YukiHookAPI.Status.isModuleActive) {
            binding.mainImgStatus.setImageResource(R.drawable.ic_success)
            binding.mainStatus.text = getString(R.string.is_active)
            binding.mainFramework.visibility = View.VISIBLE
            binding.mainFramework.text = getString(R.string.main_framework).format(YukiHookAPI.Status.Executor.name, "API ${YukiHookAPI.Status.Executor.apiLevel}")
        }
        binding.mainActiveStatus.background = getDrawable(R.drawable.bg_header)
        binding.mainVersion.text = getString(R.string.main_version)
            .format(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.mainActiveStatus.setOnClickListener {
            checkUpdate(true)
        }
        binding.mainDate.text = "Build Time：${Data.buildTime}"
        checkUpdate(false)
        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                MainActivity.searchText = s.toString()
                search(MainActivity.searchText)
            }
        })

        @Suppress("DEPRECATION")
        tabLayout.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 当前选中的标签页
                if (MainActivity.searchText.isNotEmpty()) {
                    search(binding.search.text.toString())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // 标签页从选中状态变为非选中状态时触发
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 标签页已经选中，并且再次被选中时触发
                val fragment = fragmentList[viewPager.currentItem]
                val listView = fragment.listView
                if (listView.firstVisiblePosition != 0) listView.smoothScrollToPosition(0)
                else listView.smoothScrollToPosition(listView.adapter.count)
            }
        })
    }

    fun search(text: String) {
        val fragment = fragmentList[tabLayout.selectedTabPosition]
        val list: ArrayList<AppInfo> = ArrayList()
        val texts = text.lowercase(Locale.CHINESE)
        if (MainActivity.searchText.isEmpty()) {
            for (app in fragment.list) {
                val appName = app.app_name.toString().lowercase(Locale.CHINESE)
                val appPackage = app.app_package.lowercase(Locale.CHINESE)
                if (appName.contains(texts) or appPackage.contains(texts))
                    list.add(app)
            }
        } else {
            for (app in fragment.list) {
                val appName = app.app_name.toString().lowercase(Locale.CHINESE)
                val appPackage = app.app_package.lowercase(Locale.CHINESE)
                if (appName.contains(texts) or appPackage.contains(texts))
                    list.add(app)
            }
            fragment.searchList.clear()
            fragment.searchList.addAll(list)
        }
        fragment.showList(list)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (prefs().getLong("deviceQQ", 0) == 0L) return false
        val loaded1 = fragmentList[0].loaded
        val loaded2 = fragmentList[1].loaded
        if (!loaded1 || !loaded2) {
            Toast.makeText(applicationContext, resources.getString(R.string.wait_to_load_app), Toast.LENGTH_SHORT).show()
            return false
        }
        return when (item.itemId) {
            R.id.menu_global -> {
                item.isChecked = !item.isChecked
                prefs().edit { put(Data.global, item.isChecked) }
                true
            }

            R.id.menu_show_hook_success -> {
                item.isChecked = !item.isChecked
                prefs().edit { put(Data.hookTip, item.isChecked) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_language_defualt -> {
                item.isChecked = true
                checkLanguage(Locale.getDefault())
                prefs().edit { put(Data.localeId, 0) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_language_en -> {
                item.isChecked = true
                checkLanguage(Locale.ENGLISH)
                prefs().edit { put(Data.localeId, 1) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_language_zh -> {
                item.isChecked = true
                checkLanguage(Locale.CHINESE)
                prefs().edit { put(Data.localeId, 2) }
                updateConfig(prefs().all())
                true
            }

            R.id.menu_search -> {
                val inputMethodManager: InputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                binding.search.visibility = View.VISIBLE
                binding.search.requestFocus()
                inputMethodManager.showSoftInput(binding.search, InputMethodManager.SHOW_IMPLICIT)
                true
            }

            R.id.menu_qq_group -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Data.QQ_GROUP))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    applicationContext.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, getString(R.string.failed_to_open_qq), Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.menu_color -> {
                ColorPickerPopup.Builder(this)
                    .initialColor(prefs().get(Data.themes))
                    .enableBrightness(true)
                    .enableAlpha(true)
                    .okTitle(getString(R.string.popup_done))
                    .cancelTitle(getString(R.string.popup_cancel))
                    .showIndicator(true)
                    .showValue(false)
                    .build()
                    .show(item.actionView, object : ColorPickerPopup.ColorPickerObserver() {
                        @SuppressLint("UnspecifiedImmutableFlag")
                        override fun onColorPicked(color: Int) {
                            prefs().edit { put(Data.themes, color) }
                            Thread {
                                Thread.sleep(300)
                                var intents = packageManager.getLaunchIntentForPackage(packageName)
                                if (intents != null)
                                    intents.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                else intents = Intent(applicationContext, MainActivity::class.java)
                                startActivity(intents)
                                //杀掉以前进程
                                Process.killProcess(Process.myPid())
                            }.start()
                        }
                    })
                true
            }

            R.id.menu_hide_icon -> {
                hideOrShowLauncherIcon(!item.isChecked)
                super.finish()
                true
            }

            R.id.menu_background -> {
                val filename = "background.png"
                alertimageView = ImageView(applicationContext)
                alertimageView!!.setPadding(0, 50, 0, 0)
                try {
                    alertimageView!!.setImageBitmap((imageView.drawable as BitmapDrawable).bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val dialog = AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle(getString(R.string.alert_choose_image_title))
                    .setNeutralButton(getString(R.string.alert_choose_image), null)
                    .setPositiveButton(getString(R.string.alert_done)) { dialog, _ ->
                        binding.root.background = alertimageView!!.drawable
                        try {
                            val file = File(filesDir, filename)
                            val image = (alertimageView!!.drawable as BitmapDrawable).bitmap
                            val fileOutputStream = FileOutputStream(file)
                            image.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                            fileOutputStream.flush()
                            fileOutputStream.close()
                            prefs().edit { put(Data.background, file.path) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        dialog.dismiss()
                        super.recreate()
                    }
                    .setNegativeButton(getString(R.string.alert_reset)) { dialog, _ ->
                        try {
                            val file = File(filesDir, filename)
                            if (file.exists())
                                file.delete()
                            super.recreate()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        dialog?.dismiss()
                    }
                    .setView(alertimageView)
                    .create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK, null)
                    intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    startActivityForResult(intent, 2)
                    return@setOnClickListener
                }
                true
            }

            R.id.menu_auto_skip -> {
                if (checkRoot()) {
                    if (!applicationContext.isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME)) {
                        applicationContext.runService()
                    } else {
                        applicationContext.stopService()
                    }
                } else {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                Handler().postDelayed({
                    val isChecked = isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME)
                    menu?.findItem(R.id.menu_auto_skip)?.isChecked = isChecked
                },500)
                true
            }

            R.id.menu_minimize -> {
                try {
                    val dialog = ProgressDialog(this)
                    dialog.setCancelable(false)
                    dialog.setMessage(getString(R.string.saving_configs))
                    dialog.create()
                    dialog.show()
                    Thread {
                        val config = File(filesDir, "config.json")
                        val inputStream = config.inputStream()
                        val byte = ByteArray(config.length().toInt())
                        inputStream.read(byte)
                        inputStream.close()
                        val outputStream = FileOutputStream(config)
                        outputStream.write(JSONObject(String(byte)).formatJson())
                        outputStream.flush()
                        outputStream.close()
                        super.finishAndRemoveTask()
                        if (!applicationContext.isAccessibilitySettingsOn(BuildConfig.SERVICE_NAME) &&
                            menu?.findItem(R.id.menu_auto_skip)?.isChecked == true
                        ) {
                            applicationContext.runService()
                        }
                        Looper.prepare()
                        Process.killProcess(Process.myPid())
                    }.start()
                } catch (e : Exception) {
                    Log.i("minimize")
                }
                true
            }

            R.id.menu_github -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hujiayucc/Fuck-AD"))
                    startActivity(intent)
                } catch (e : Exception) {
                    e.printStackTrace()
                }
                true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("ResourceAsColor")
    private fun initBackGround() {
        imageView = ImageView(applicationContext)
        try {
            val background = prefs().get(Data.background)
            imageView.setImageBitmap(BitmapFactory.decodeFile(background))
            binding.root.background = imageView.drawable
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) {
            // 从相册返回的数据
            if (data != null) {
                alertimageView!!.setImageURI(data.data)
            }
        }
    }

    @SuppressLint("ResourceType")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (binding.search.visibility != View.GONE) {
                    binding.search.setText("")
                    binding.search.visibility = View.GONE
                    false
                } else {
                    finish()
                    true
                }
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }
}