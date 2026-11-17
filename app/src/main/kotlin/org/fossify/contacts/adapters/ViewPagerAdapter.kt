package org.fossify.contacts.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.fragments.MyViewPagerFragment

// One page per entry of [pages] (commons TAB_* masks), in that order.
class ViewPagerAdapter(val activity: SimpleActivity, private val pages: List<Int>) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment<*>).apply {
            setupFragment(activity)
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor())
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = pages.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int) = when (pages[position]) {
        TAB_FAVORITES -> R.layout.fragment_favorites
        TAB_CONTACTS -> R.layout.fragment_contacts
        else -> R.layout.fragment_groups
    }
}
