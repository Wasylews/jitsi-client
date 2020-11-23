package com.connect.club.jitsiclient

class ViewFactory(
    private val viewList: List<JitsiView>
) {

    private val remoteViews = mutableMapOf<String, JitsiView>()
    private var currentViewIndex = 1

    fun getLocalView(): JitsiView {
        return viewList[0]
    }

    fun getRemoteView(streamId: String): JitsiView? {
        return remoteViews.getOrPut(streamId) {
            viewList[currentViewIndex++]
        }
    }
}