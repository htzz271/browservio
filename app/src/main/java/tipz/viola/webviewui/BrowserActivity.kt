/*
 * Copyright (C) 2020-2023 Tipz Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tipz.viola.webviewui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.JsonReader
import android.util.JsonToken
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tipz.viola.Application
import tipz.viola.R
import tipz.viola.broha.ListInterfaceActivity
import tipz.viola.broha.api.FavUtils
import tipz.viola.broha.database.IconHashUtils
import tipz.viola.search.SearchEngineEntries
import tipz.viola.search.SuggestionAdapter
import tipz.viola.settings.SettingsActivity
import tipz.viola.settings.SettingsKeys
import tipz.viola.utils.CommonUtils
import tipz.viola.utils.InternalUrls
import tipz.viola.webview.VWebViewActivity
import tipz.viola.webviewui.view.CentreSpreadItemDecoration
import java.io.IOException
import java.io.StringReader
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.Objects


@Suppress("DEPRECATION")
class BrowserActivity : VWebViewActivity() {
    private var urlEditText: MaterialAutoCompleteTextView? = null
    private var upRightFab: AppCompatImageView? = null
    private var currentPrebuiltUAState = false
    private var currentCustomUA: String? = null
    private var currentCustomUAWideView = false
    private var iconHashClient: IconHashUtils? = null
    private var toolBar: RecyclerView? = null
    private var toolsBarExtendableRecycler: RecyclerView? = null
    private var toolsBarExtendableBackground: ConstraintLayout? = null
    private var toolsBarExtendableCloseHitBox: LinearLayoutCompat? = null
    private var viewMode: Int = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        // Initialize variables
        upRightFab = findViewById(R.id.upRightFab)
        urlEditText = findViewById(R.id.urlEditText)
        progressBar = findViewById(R.id.webviewProgressBar)
        faviconProgressBar = findViewById(R.id.faviconProgressBar)
        swipeRefreshLayout = findViewById(R.id.layout_webview)
        webview = swipeRefreshLayout.findViewById(R.id.webview)
        favicon = findViewById(R.id.favicon)
        startPageLayout = findViewById(R.id.layout_startpage)
        iconHashClient = (applicationContext as Application).iconHashClient

        // Setup toolbar
        toolBar = findViewById(R.id.toolBar)
        toolBar?.adapter = ItemsAdapter(this, toolsBarItemList)
        toolBar?.addItemDecoration(
                CentreSpreadItemDecoration(
                        resources.getDimension(R.dimen.actionbar_content_height), toolsBarItemList.size,
                        isLinear = true
                )
        )

        // Setup toolbar expandable
        toolsBarExtendableRecycler = findViewById(R.id.toolsBarExtendableRecycler)
        toolsBarExtendableRecycler?.adapter =
                ToolbarItemsAdapter(this, toolsBarExpandableItemList, toolsBarExpandableDescriptionList)
        toolsBarExtendableRecycler?.addItemDecoration(
                CentreSpreadItemDecoration(
                        resources.getDimension(R.dimen.toolbar_extendable_holder_size),
                        toolsBarItemList.size,
                        isLinear = false
                )
        )
        toolsBarExtendableBackground = this.findViewById(R.id.toolsBarExtendableBackground)
        toolsBarExtendableBackground!!.post {
            toolsBarExtendableBackground!!.visibility = View.GONE
        }
        toolsBarExtendableCloseHitBox = this.findViewById(R.id.toolsBarExtendableCloseHitBox)
        toolsBarExtendableCloseHitBox?.setOnClickListener {
            expandToolBar()
        }

        // Setup favicon
        favicon?.setOnClickListener {
            val cert = webview.certificate
            val popupMenu = PopupMenu(this, favicon!!)
            val menu = popupMenu.menu
            menu.add(if (webview.visibility == View.GONE) resources.getString(R.string.start_page) else webview.title).isEnabled = false
            menu.add(resources.getString(R.string.copy_title))
            if (cert != null) menu.add(resources.getString(R.string.ssl_info))
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                if (item.title.toString() == resources.getString(R.string.copy_title)) {
                    CommonUtils.copyClipboard(this@BrowserActivity, webview.title)
                    return@setOnMenuItemClickListener true
                } else if (item.title.toString() == resources.getString(R.string.ssl_info)) {
                    val issuedTo = cert!!.issuedTo
                    val issuedBy = cert.issuedBy
                    val dialog = MaterialAlertDialogBuilder(this@BrowserActivity)
                    dialog.setTitle(Uri.parse(webview.url).host)
                            .setMessage(
                                    resources.getString(
                                            R.string.ssl_info_dialog_content,
                                            issuedTo.cName, issuedTo.oName, issuedTo.uName,
                                            issuedBy.cName, issuedBy.oName, issuedBy.uName,
                                            DateFormat.getDateTimeInstance()
                                                    .format(cert.validNotBeforeDate),
                                            DateFormat.getDateTimeInstance().format(cert.validNotAfterDate)
                                    )
                            )
                            .setPositiveButton(resources.getString(android.R.string.ok), null)
                            .create().show()
                    return@setOnMenuItemClickListener true
                }
                false
            }
            popupMenu.show()
        }

        // Setup Url EditText box
        urlEditText?.setOnEditorActionListener(
                OnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
                    if (actionId == EditorInfo.IME_ACTION_GO || actionId == KeyEvent.ACTION_DOWN) {
                        webview.loadUrl(urlEditText?.text.toString())
                        urlEditText?.clearFocus()
                        return@OnEditorActionListener true
                    }
                    false
                })
        urlEditText?.setOnFocusChangeListener { _: View?, hasFocus: Boolean ->
            if (!hasFocus) {
                if (urlEditText?.text
                                .toString() != webview.url
                ) urlEditText?.setText(webview.url)
                urlEditText?.setSelection(0)
                urlEditText?.dropDownHeight = 0
                closeKeyboard()
            } else {
                urlEditText?.dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        urlEditText?.setOnClickListener {
            if (toolsBarExtendableBackground?.visibility == View.VISIBLE) expandToolBar()
        }
        urlEditText?.onItemClickListener =
                OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
                    webview.loadUrl((view.findViewById<View>(android.R.id.text1) as AppCompatTextView).text.toString())
                    closeKeyboard()
                }
        urlEditText?.setAdapter(
                SuggestionAdapter(
                        this@BrowserActivity,
                        R.layout.recycler_list_item_1
                )
        )

        // Setup the up most fab (currently for reload)
        upRightFab?.setOnClickListener {
            if (progressBar.progress > 0) webview.stopLoading()
            if (progressBar.progress == 0) webview.webViewReload()
        }

        // Finally, setup WebView
        webViewInit()
    }

    /* Init VioWebView */
    private fun webViewInit() {
        webview.notifyViewSetup()
        val dataUri = intent.data
        if (dataUri != null) {
            webview.loadUrl(dataUri.toString())
        } else if (settingsPreference.getIntBool(SettingsKeys.useWebHomePage)) {
            webview.loadUrl(SearchEngineEntries.getHomePageUrl(
                    settingsPreference,
                    settingsPreference.getInt(SettingsKeys.defaultHomePageId)))
        } else {
            webview.loadUrl(InternalUrls.startUrl)
        }
    }

    // https://stackoverflow.com/a/57840629/10866268
    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.clear()
    }

    @SuppressLint("NotifyDataSetChanged") // We want it to scan the whole dataset
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val orientation: Int = getResources().configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE || orientation == Configuration.ORIENTATION_PORTRAIT) {
            toolBar?.adapter?.notifyDataSetChanged()
            (toolsBarExtendableRecycler?.layoutManager!! as GridLayoutManager).spanCount = resources.getInteger(R.integer.num_toolbar_expandable_items_per_row)
            toolsBarExtendableRecycler?.adapter?.notifyDataSetChanged()
        }
    }

    override fun doSettingsCheck() {
        super.doSettingsCheck()
        val reverseAddressBar: Int = settingsPreference.getInt(SettingsKeys.reverseAddressBar)
        if (reverseAddressBar != viewMode) {
            val constraintLayout = findViewById<ConstraintLayout>(R.id.mainBackground)
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.clear(R.id.appbar, ConstraintSet.TOP)
            constraintSet.clear(R.id.appbar, ConstraintSet.BOTTOM)
            constraintSet.clear(R.id.webviewContainer, ConstraintSet.TOP)
            constraintSet.clear(R.id.toolsContainer, ConstraintSet.BOTTOM)
            if (reverseAddressBar == 1) {
                constraintSet.connect(
                        R.id.appbar,
                        ConstraintSet.TOP,
                        R.id.toolsContainer,
                        ConstraintSet.BOTTOM,
                        0
                )
                constraintSet.connect(
                        R.id.appbar,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                        0
                )
                constraintSet.connect(
                        R.id.webviewContainer,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                        0
                )
                constraintSet.connect(
                        R.id.toolsContainer,
                        ConstraintSet.BOTTOM,
                        R.id.appbar,
                        ConstraintSet.TOP,
                        0
                )
            } else {
                constraintSet.connect(
                        R.id.appbar,
                        ConstraintSet.BOTTOM,
                        R.id.webviewContainer,
                        ConstraintSet.TOP,
                        0
                )
                constraintSet.connect(
                        R.id.appbar,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                        0
                )
                constraintSet.connect(
                        R.id.webviewContainer,
                        ConstraintSet.TOP,
                        R.id.appbar,
                        ConstraintSet.BOTTOM,
                        0
                )
                constraintSet.connect(
                        R.id.toolsContainer,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                        0
                )
            }
            constraintSet.applyTo(constraintLayout)
            viewMode = reverseAddressBar
        }
    }

    fun itemSelected(view: AppCompatImageView?, item: Int) {
        when (item) {
            R.drawable.arrow_back_alt -> if (webview.canGoBack()) webview.goBack()
            R.drawable.arrow_forward_alt -> if (webview.canGoForward()) webview.goForward()
            R.drawable.refresh -> webview.webViewReload()
            R.drawable.home -> {
                if (settingsPreference.getIntBool(SettingsKeys.useWebHomePage)) {
                    webview.loadUrl(
                            SearchEngineEntries.getHomePageUrl(
                                    settingsPreference,
                                    settingsPreference.getInt(SettingsKeys.defaultHomePageId)
                            )
                    )
                } else {
                    urlEditText!!.setText(CommonUtils.EMPTY_STRING)
                    webview.loadUrl(InternalUrls.startUrl)
                }
            }

            R.drawable.smartphone, R.drawable.desktop, R.drawable.custom -> {
                currentPrebuiltUAState = !currentPrebuiltUAState
                webview.setPrebuiltUAMode(
                        view, if (currentPrebuiltUAState) 1 else 0, false
                )
            }

            R.drawable.share -> CommonUtils.shareUrl(this, webview.url)
            R.drawable.app_shortcut -> {
                if (webview.title.isNullOrBlank() || webview.url.isNullOrBlank()) return
                ShortcutManagerCompat.requestPinShortcut(
                        this, ShortcutInfoCompat.Builder(this, webview.title!!)
                        .setShortLabel(webview.title!!)
                        .setIcon(
                                IconCompat.createWithBitmap(
                                        CommonUtils.drawableToBitmap(favicon!!.drawable)
                                )
                        )
                        .setIntent(
                                Intent(this, BrowserActivity::class.java)
                                        .setData(Uri.parse(webview.url))
                                        .setAction(Intent.ACTION_VIEW)
                        )
                        .build(), null
                )
            }

            R.drawable.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                mGetNeedLoad.launch(intent)
            }

            R.drawable.history -> {
                val intent = Intent(this@BrowserActivity, ListInterfaceActivity::class.java)
                intent.putExtra(Intent.EXTRA_TEXT, ListInterfaceActivity.mode_history)
                mGetNeedLoad.launch(intent)
            }

            R.drawable.favorites -> {
                val intent = Intent(this@BrowserActivity, ListInterfaceActivity::class.java)
                intent.putExtra(Intent.EXTRA_TEXT, ListInterfaceActivity.mode_favorites)
                mGetNeedLoad.launch(intent)
            }

            R.drawable.favorites_add -> {
                val url = webview.url
                if (url.isNullOrBlank()) return

                val icon = favicon!!.drawable
                val title = webview.title

                CoroutineScope(Dispatchers.IO).launch {
                    FavUtils.appendData(
                            this@BrowserActivity, iconHashClient, title, url,
                            if (icon is BitmapDrawable) icon.bitmap else null
                    )
                }
                CommonUtils.showMessage(
                        this,
                        resources.getString(R.string.save_successful)
                )
            }

            R.drawable.close -> finish()
            R.drawable.view_stream -> expandToolBar()
            R.drawable.code -> {
                webview.evaluateJavascript(
                        "document.documentElement.outerHTML",
                        ValueCallback { value: String? ->
                            val reader = JsonReader(StringReader(value))
                            reader.isLenient = true
                            try {
                                if (reader.peek() == JsonToken.STRING) {
                                    val domStr = reader.nextString()
                                    reader.close()
                                    if (domStr == null) return@ValueCallback
                                    MaterialAlertDialogBuilder(this@BrowserActivity)
                                            .setTitle(resources.getString(R.string.toolbar_expandable_view_page_source))
                                            .setMessage(domStr)
                                            .setPositiveButton(
                                                    resources.getString(android.R.string.ok),
                                                    null
                                            )
                                            .setNegativeButton(resources.getString(android.R.string.copy)) { _: DialogInterface?, _: Int ->
                                                CommonUtils.copyClipboard(
                                                        this@BrowserActivity,
                                                        domStr
                                                )
                                            }
                                            .create().show()
                                }
                            } catch (ignored: IOException) {
                            }
                        })
            }

            R.drawable.new_tab -> {
                val i = Intent(this, BrowserActivity::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                else i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                startActivity(i)
            }
        }
    }

    fun itemLongSelected(view: AppCompatImageView?, item: Int) {
        if (item == R.drawable.smartphone || item == R.drawable.desktop || item == R.drawable.custom) {
            val layoutInflater = LayoutInflater.from(this)
            @SuppressLint("InflateParams") val root =
                    layoutInflater.inflate(R.layout.dialog_ua_edit, null)
            val customUserAgent = root.findViewById<AppCompatEditText>(R.id.edittext)
            val deskMode = root.findViewById<AppCompatCheckBox>(R.id.deskMode)
            deskMode.isChecked = currentCustomUAWideView
            val dialog = MaterialAlertDialogBuilder(this)
            dialog.setTitle(resources.getString(R.string.customUA))
                    .setView(root)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        if (customUserAgent.length() != 0) webview.setUA(
                                view, deskMode.isChecked,
                                Objects.requireNonNull(customUserAgent.text).toString(),
                                R.drawable.custom, false
                        )
                        currentCustomUA = Objects.requireNonNull(customUserAgent.text).toString()
                        currentCustomUAWideView = deskMode.isChecked
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create().show()
            if (currentCustomUA != null) customUserAgent.setText(currentCustomUA)
        }
    }

    fun expandToolBar() {
        val viewVisible: Boolean = toolsBarExtendableBackground!!.visibility == View.VISIBLE
        val transition: Transition = Slide(Gravity.BOTTOM)
        transition.duration =
                (resources.getInteger(R.integer.anim_expandable_speed) * Settings.Global.getFloat(
                        contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1.0f
                )).toLong()
        transition.addTarget(R.id.toolsBarExtendableBackground)
        TransitionManager.beginDelayedTransition(toolsBarExtendableBackground!!, transition)
        toolsBarExtendableBackground!!.visibility = if (viewVisible) View.GONE else View.VISIBLE
        toolsBarExtendableCloseHitBox!!.visibility = if (viewVisible) View.GONE else View.VISIBLE
    }

    override fun onPageLoadProgressChanged(progress: Int) {
        super.onPageLoadProgressChanged(progress)
        if (progress == -1) upRightFab?.setImageResource(R.drawable.stop)
        if (progress == 0) upRightFab?.setImageResource(R.drawable.refresh)
    }

    private fun closeKeyboard() {
        WindowCompat.getInsetsController(window, urlEditText!!).hide(WindowInsetsCompat.Type.ime())
    }


    override fun onUrlUpdated(url: String?) {
        if (!urlEditText!!.isFocused) urlEditText!!.setText(url)
    }

    override fun onUrlUpdated(url: String?, position: Int) {
        urlEditText!!.setText(url)
        urlEditText!!.setSelection(position)
    }

    override fun onDropDownDismissed() {
        urlEditText!!.dismissDropDown()
        urlEditText!!.clearFocus()
    }

    override fun onStartPageEditTextPressed() {
        urlEditText!!.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(urlEditText, InputMethodManager.SHOW_FORCED)
    }

    class ItemsAdapter(mainActivity: BrowserActivity, itemsList: List<Int>) :
            RecyclerView.Adapter<ItemsAdapter.ViewHolder>() {
        private val mBrowserActivity: WeakReference<BrowserActivity>
        private val mItemsList: WeakReference<List<Int>>

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val mImageView: AppCompatImageView

            init {
                mImageView = view.findViewById(R.id.imageView)
            }
        }

        init {
            mBrowserActivity = WeakReference(mainActivity)
            mItemsList = WeakReference(itemsList)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.recycler_icon_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.mImageView.setImageResource(mItemsList.get()!![position])
            holder.mImageView.setOnClickListener {
                mBrowserActivity.get()!!
                        .itemSelected(holder.mImageView, mItemsList.get()!![position])
            }
            holder.mImageView.setOnLongClickListener {
                mBrowserActivity.get()!!
                        .itemLongSelected(holder.mImageView, mItemsList.get()!![position])
                true
            }
        }

        override fun getItemCount(): Int {
            return mItemsList.get()!!.size
        }
    }

    class ToolbarItemsAdapter(
            mainActivity: BrowserActivity,
            itemsList: List<Int>,
            descriptionList: List<Int>
    ) :
            RecyclerView.Adapter<ToolbarItemsAdapter.ViewHolder>() {
        private val mBrowserActivity: WeakReference<BrowserActivity>
        private val mItemsList: WeakReference<List<Int>>
        private val mDescriptionList: WeakReference<List<Int>>

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val mItemBox: LinearLayoutCompat
            val mImageView: AppCompatImageView
            val mTextView: AppCompatTextView

            init {
                mItemBox = view as LinearLayoutCompat
                mImageView = view.findViewById(R.id.imageView)
                mTextView = view.findViewById(R.id.textView)
            }
        }

        init {
            mBrowserActivity = WeakReference(mainActivity)
            mItemsList = WeakReference(itemsList)
            mDescriptionList = WeakReference(descriptionList)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.recycler_toolsbar_expandable_icon_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.mItemBox.setOnClickListener {
                mBrowserActivity.get()!!
                        .itemSelected(holder.mImageView, mItemsList.get()!![position])
                mBrowserActivity.get()!!.expandToolBar()
            }
            holder.mItemBox.setOnLongClickListener {
                mBrowserActivity.get()!!
                        .itemLongSelected(holder.mImageView, mItemsList.get()!![position])
                true
            }
            holder.mImageView.setImageResource(mItemsList.get()!![position])
            holder.mTextView.text =
                    mBrowserActivity.get()!!.resources.getString(mDescriptionList.get()!![position])
        }

        override fun getItemCount(): Int {
            return mItemsList.get()!!.size
        }
    }

    companion object {
        // TODO: Add support for reverting to legacy layout
        private val legacyToolsBarItemList = listOf(
                R.drawable.arrow_back_alt,
                R.drawable.arrow_forward_alt,
                R.drawable.refresh,
                R.drawable.home,
                R.drawable.smartphone,
                R.drawable.new_tab,
                R.drawable.share,
                R.drawable.app_shortcut,
                R.drawable.settings,
                R.drawable.history,
                R.drawable.favorites,
                R.drawable.close
        )

        private val toolsBarItemList = listOf(
                R.drawable.arrow_back_alt,
                R.drawable.arrow_forward_alt,
                R.drawable.home,
                R.drawable.share,
                R.drawable.view_stream
        )

        private val toolsBarExpandableItemList = listOf(
                R.drawable.new_tab,
                R.drawable.favorites,
                R.drawable.history,
                R.drawable.smartphone,
                R.drawable.favorites_add,
                R.drawable.app_shortcut,
                R.drawable.settings,
                R.drawable.code,
                R.drawable.close
        )

        private val toolsBarExpandableDescriptionList = listOf(
                R.string.toolbar_expandable_new_tab,
                R.string.toolbar_expandable_favorites,
                R.string.toolbar_expandable_history,
                R.string.toolbar_expandable_viewport,
                R.string.toolbar_expandable_favorites_add,
                R.string.toolbar_expandable_app_shortcut,
                R.string.toolbar_expandable_settings,
                R.string.toolbar_expandable_view_page_source,
                R.string.toolbar_expandable_close
        )
    }
}