package com.inspiredandroid.linuxcommandbibliotheca.fragments

import android.app.SearchManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.SearchView
import android.text.Html
import android.text.Spanned
import android.view.*
import com.google.android.gms.appindexing.Action
import com.google.firebase.analytics.FirebaseAnalytics
import com.inspiredandroid.linuxcommandbibliotheca.BuildConfig
import com.inspiredandroid.linuxcommandbibliotheca.CommandManActivity
import com.inspiredandroid.linuxcommandbibliotheca.R
import com.inspiredandroid.linuxcommandbibliotheca.adapter.ManAdapter
import com.inspiredandroid.linuxcommandbibliotheca.misc.AppManager
import com.inspiredandroid.linuxcommandbibliotheca.misc.FragmentCoordinator
import com.inspiredandroid.linuxcommandbibliotheca.models.Command
import com.inspiredandroid.linuxcommandbibliotheca.models.CommandPage
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.android.synthetic.main.fragment_command_man.*
import java.util.*

/**
 * Created by Simon Schubert
 */
class CommandManFragment : AppIndexFragment(), View.OnClickListener {

    private var mAdapter: ManAdapter? = null
    private var mRealm: Realm? = null
    private var mFirebaseAnalytics: FirebaseAnalytics? = null
    private var mName: String? = null
    private var mId: Long = 0
    private var mCategory: Int = 0
    private var mIndexesPosition: Int = 0
    private val query: String? = null
    private val indexes = ArrayList<Int>()

    private fun fromHtml(html: String?): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)!!
        } else {
            Html.fromHtml(html)
        }
    }

    override fun getAppIndexingTitle(): String {
        return "$mName($mCategory) man page"
    }

    override fun getAppIndexingAction(): Action {
        val APP_URI = Uri.parse("android-app://" + BuildConfig.APPLICATION_ID + "/http/linux.schubert-simon.de/mans")
        val WEB_URL = Uri.parse("http://linux.schubert-simon.de/mans/")
        return Action.newAction(Action.TYPE_VIEW, getAppIndexingTitle(), WEB_URL, APP_URI)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FragmentCoordinator.isTabletLayout(activity!!)) {
            setHasOptionsMenu(true)
        }

        // Get unique command mId
        val b = arguments
        mId = b!!.getLong(CommandManActivity.EXTRA_COMMAND_ID)
        mName = b.getString(CommandManActivity.EXTRA_COMMAND_NAME)
        mCategory = b.getInt(CommandManActivity.EXTRA_COMMAND_CATEGORY)

        mRealm = Realm.getDefaultInstance()

        mAdapter = createAdapter()

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context!!)

        val realm = Realm.getDefaultInstance()
        val command = realm.where(Command::class.java).equalTo(Command.ID, mId).findFirst()
        if (command != null) {
            trackSelectContent(command.name)
        }
        realm.close()
    }

    private fun trackSelectContent(name: String?) {
        if (BuildConfig.DEBUG) {
            return
        }
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, name)
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Manual Detail")
        mFirebaseAnalytics!!.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_command_man, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = mAdapter

        fragment_command_man_btn_up.setOnClickListener(this)
        fragment_command_man_btn_down.setOnClickListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        mRealm!!.close()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.man, menu)

        // Associate searchable configuration with the SearchView
        val searchManager = activity!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val item = menu!!.findItem(R.id.search)
        val searchView = item.actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity!!.componentName))

        // Associate searchable configuration with the SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (query.isNotEmpty()) {
                    search(query)
                } else {
                    resetSearchResults()
                }
                return true
            }
        })
        MenuItemCompat.setOnActionExpandListener(item, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                resetSearchResults()
                return true
            }
        })

        val bookmarkItem = menu.findItem(R.id.bookmark)
        bookmarkItem.setIcon(if (AppManager.hasBookmark(context!!, mId)) R.drawable.ic_bookmark_black_24dp else R.drawable.ic_bookmark_border_black_24dp)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.bookmark) {
            toogleBookmark()
            activity?.invalidateOptionsMenu()
            return true
        }
        return false
    }

    /**
     * Split long page text into child mRecyclerView views
     *
     * @return
     */
    private fun createAdapter(): ManAdapter {
        val pages = mRealm!!.where<CommandPage>().equalTo(CommandPage.COMMANDID, mId).findAll()

        val groups = ArrayList<String>()
        val child = ArrayList<ArrayList<CharSequence>>()
        for (page in pages) {

            groups.add(page.title!!)

            val pageSplit = ArrayList<CharSequence>()
            val tmp = page.page.toString().split("</p>".toRegex()).toTypedArray()
            for (tmpSplit in tmp) {
                val p = trim(fromHtml("$tmpSplit</p>"))
                if(p.isNotEmpty()) {
                    pageSplit.add(p)
                }
            }
            child.add(pageSplit)
        }

        return ManAdapter(groups, child)
    }

    private fun trim(data: CharSequence): CharSequence {
        var text = data
        if(text.isEmpty()) {
            return text
        }
        while (text[text.length - 1] == '\n') {
            text = text.subSequence(0, text.length - 1)
        }
        while (text[0] == '\n') {
            text = text.subSequence(1, text.length)
        }
        return text
    }

    private fun toogleBookmark() {
        if (AppManager.hasBookmark(context!!, mId)) {
            AppManager.removeBookmark(context!!, mId)
        } else {
            AppManager.addBookmark(context!!, mId)
        }
    }

    /**
     *
     */
    private fun resetSearchResults() {
        /*
        val async = SearchManAsyncTask(context!!, "", mAdapter!!.mChild, this)
        addAsyncTask(async)
        async.execute()

        hideButton()
        */
    }

    /**
     * Search the man page for q occurs and highlight them and jump to first occur
     *
     * @param q
     */
    private fun search(q: String) {
        /*
        val async = SearchManAsyncTask(context!!, q, mAdapter!!.mChild, this)
        addAsyncTask(async)
        async.execute()
        */
    }

    /**
     * Get line by index of occur and calculate position
     *
     * @param index
     */
    private fun scrollToPosition(index: Int) {
        /*
        int line = tvDescription.getLayout().getLineForOffset(index);
        int lineHeight = tvDescription.getLayout().getHeight() / tvDescription.getLayout().getLineCount();
        int position = line * lineHeight;
        scrollView.scrollTo(0, position);
        */
    }

    /**
     *
     */
    private fun hideButton() {
        fragment_command_man_btn_up.visibility = View.GONE
        fragment_command_man_btn_down.visibility = View.GONE
    }

    /**
     *
     */
    private fun showButton() {
        fragment_command_man_btn_up.visibility = View.VISIBLE
        fragment_command_man_btn_down.visibility = View.VISIBLE
    }

    /**
     * go to next, if last then go to first
     */
    private fun jumpToNextPosition() {
        mIndexesPosition--
        if (mIndexesPosition < 0) {
            mIndexesPosition = indexes.size - 1
        }
        scrollToPosition(indexes[mIndexesPosition])
    }

    /**
     * go to previous, if smaller 0 then go to last
     */
    private fun jumpToPreviousPosition() {
        mIndexesPosition++
        if (mIndexesPosition >= indexes.size) {
            mIndexesPosition = 0
        }
        scrollToPosition(indexes[mIndexesPosition])
    }

    override fun onClick(v: View) {
        if (v.id == R.id.fragment_command_man_btn_up) {
            jumpToNextPosition()
        } else if (v.id == R.id.fragment_command_man_btn_down) {
            jumpToPreviousPosition()
        }
    }
}