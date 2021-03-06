package com.ohelshem.app.android.changes

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.Menu
import android.view.MenuInflater
import com.github.salomonbrys.kodein.erased.instance
import com.ohelshem.api.model.Change
import com.ohelshem.api.model.UpdateError
import com.ohelshem.app.android.changes.clazz.ClassChangesFragment
import com.ohelshem.app.android.changes.layer.LayerChangesFragment
import com.ohelshem.app.android.changes.layer.LayerChangesPresenter
import com.ohelshem.app.android.drawableRes
import com.ohelshem.app.android.hide
import com.ohelshem.app.android.main.MainActivity
import com.ohelshem.app.android.show
import com.ohelshem.app.android.stringResource
import com.ohelshem.app.android.utils.BaseMvpFragment
import com.ohelshem.app.changesDateFormat
import com.ohelshem.app.controller.timetable.TimetableController.Companion.DayType
import com.ohelshem.app.day
import com.ohelshem.app.toCalendar
import com.yoavst.changesystemohelshem.R
import kotlinx.android.synthetic.main.changes_fragment.*
import org.jetbrains.anko.support.v4.toast
import java.io.File
import java.util.*

class ChangesFragment : BaseMvpFragment<ChangesView, LayerChangesPresenter>(), ChangesView {
    private val day by stringResource(R.string.day)
    private val weekDays by lazy { resources.getStringArray(R.array.week_days) }

    override val layoutId: Int = R.layout.changes_fragment
    override var menuId: Int = R.menu.changes

    override fun createPresenter(): LayerChangesPresenter = with(kodein()) { LayerChangesPresenter(instance(), instance(), instance()) }

    override fun init() {
        screenManager.setToolbarElevation(false)

        initPager()
    }

    override fun onResume() {
        super.onResume()
        presenter.update()
    }

    private fun initPager() {
        pager.adapter = ChangesFragmentAdapter(childFragmentManager)
    }

    private fun initTabs() {
        if (isShowingData) {
            val tabs = screenManager.inlineTabs

            if (tabs.tabCount == 0) {
                tabs.setupWithViewPager(pager)
                tabs.getTabAt(0)!!.icon = drawableRes(R.drawable.ic_list)
                tabs.getTabAt(1)!!.icon = drawableRes(R.drawable.ic_table)
            }
        } else setTitle()
    }

    override fun onError(error: UpdateError) {
        screenManager.screenTitle = getString(R.string.changes)
        when (error) {
            UpdateError.NoData -> {
                progressActivity.showError(drawableRes(R.drawable.ic_sync_problem), getString(R.string.no_data), getString(R.string.no_data_subtitle),
                        getString(R.string.refresh)) {
                    presenter.refresh(screenManager)
                }
            }
            UpdateError.Connection -> {
                progressActivity.showError(drawableRes(R.drawable.ic_sync_problem), getString(R.string.no_connection), getString(R.string.no_connection_subtitle),
                        getString(R.string.try_again)) {
                    presenter.refresh(screenManager)
                }
            }
            else -> {
                progressActivity.showError(drawableRes(R.drawable.ic_error), getString(R.string.general_error), getString(R.string.try_again), getString(R.string.try_again)) {
                    presenter.refresh(screenManager)
                }
            }
        }
        dateLayout.hide()
    }

    /**
     * Layer empty data, therefore there are no changes and it is safe to hide the tabs.
     */
    override fun onEmptyData(dayType: DayType) {
        if (progressActivity.isError)
            progressActivity.showContent()
        setTitle()
        if (dayType == DayType.Holiday || dayType == DayType.Summer) {
            progressActivity.showEmpty(drawableRes(R.drawable.ic_beach), getString(R.string.holiday_today), getString(R.string.holiday_today_subtitle))
        } else {
            when (dayType) {
                DayType.Saturday -> progressActivity.showEmpty(drawableRes(R.drawable.ic_beach), getString(R.string.shabat_today), getString(R.string.shabat_today_subtitle))
                DayType.Friday -> progressActivity.showEmpty(drawableRes(R.drawable.ic_beach), getString(R.string.friday_today), getString(R.string.friday_today_subtitle))
                else -> progressActivity.showError(drawableRes(R.drawable.ic_error), getString(R.string.no_changes), getString(R.string.no_changes_subtitle), getString(R.string.go_to_timetable)) {
                    presenter.launchTimetableScreen(screenManager)
                }
            }
        }
        dateLayout.hide()
    }

    override fun onBecomingVisible() = initTabs()

    @SuppressLint("SetTextI18n")
    override fun setData(changes: List<Change>) {
        initTabs()
        dateLayout.show()
        date.text = changesDateFormat.format(Date(presenter.changesDate))
        nameDay.text = "$day ${weekDays[presenter.changesDate.toCalendar().day - 1]}"

        childFragmentManager.fragments?.forEach {
            ((it as? BaseChangesFragment<*>)?.getPresenter() as? IBaseChangesPresenter)?.update()
        }
    }

    override val isShowingData: Boolean
        get() = presenter.hasData


    private fun setTitle() {
        val data = presenter.changesDate
        screenManager.screenTitle = day + " " + weekDays[data.toCalendar().day - 1] + " " + changesDateFormat.format(Date(data))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.share).setOnMenuItemClickListener {
            if (isShowingData && presenter.lastChanges != null) {
                if (!isSharing) {
                    isSharing = true
                    val file = File(File(context!!.filesDir, MainActivity.SharingFolder).apply { mkdirs() }, SharingFilename)
                    val uri = Uri.parse("content://${context!!.packageName}/${MainActivity.SharingFolder}/$SharingFilename")
                    toast(R.string.generating_data)
                    LayerChangesGenerator.generateLayerChanges(context!!, presenter.lastChanges!!, presenter.classesAtLayer, presenter.userLayer, file) {
                        val shareIntent = Intent(Intent.ACTION_SEND)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .setDataAndType(uri, "image/png")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                        isSharing = false
                    }
                }
            } else {
                toast(R.string.no_changes)
            }
            true
        }
    }

    private var isSharing: Boolean = false

    class ChangesFragmentAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {
        override fun getItem(position: Int): Fragment = if (position == 0) ClassChangesFragment() else LayerChangesFragment()

        override fun getCount(): Int = 2

    }

    companion object {
        private const val SharingFilename = "layer_changes.png"
    }
}