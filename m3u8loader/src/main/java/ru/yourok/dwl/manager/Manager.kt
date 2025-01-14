package ru.yourok.dwl.manager

import android.app.Activity
import android.support.v7.app.AlertDialog
import ru.yourok.dwl.downloader.Downloader
import ru.yourok.dwl.downloader.LoadState
import ru.yourok.dwl.downloader.State
import ru.yourok.dwl.list.List
import ru.yourok.dwl.storage.Storage
import ru.yourok.dwl.utils.Loader
import ru.yourok.dwl.utils.Saver
import ru.yourok.m3u8loader.App
import ru.yourok.m3u8loader.R
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread


object Manager {
    @Volatile
    private var loaderList: MutableList<Downloader> = mutableListOf()
    @Volatile
    private var queueList: MutableList<Int> = mutableListOf()

    init {
        val list = Loader.loadLists()
        if (list != null)
            list.forEach {
                loaderList.add(Downloader(it))
            }
    }

    fun getLoader(index: Int): Downloader? {
        if (index in 0 until loaderList.size)
            return loaderList[index]
        return null
    }

    fun findLoader(list: List): Downloader? {
        loaderList.forEach {
            if (it.list.url + it.list.title == list.url + list.title)
                return it
        }
        return null
    }

    fun getLoadersSize(): Int {
        return loaderList.size
    }

    fun getLoaderStat(i: Int): State? {
        return getLoader(i)?.getState()
    }

    fun addList(list: MutableList<List>) {
        synchronized(loaderList) {
            list.forEach {
                var isFindUrl = false
                loaderList.forEach { item ->
                    if (it.url == item.list.url)
                        throw IOException(App.getContext().getString(R.string.error_same_url))
                }
                if (!isFindUrl) {
                    //find equal url
                    val flist = loaderList.find { downloader ->
                        downloader.getState().name == it.title
                    }
                    //replace urls if find eq
                    if (flist != null) {
                        flist.list.url = it.url
                        if (flist.list.items.size != it.items.size) {
                            it.items = flist.list.items
                        } else
                            flist.list.items.forEachIndexed { index, item ->
                                if (index < it.items.size)
                                    item.url = it.items[index].url
                            }

                    } else
                        loaderList.add(Downloader(it))
                }
            }
            list.forEach { Saver.saveList(it) }
        }
    }

    fun removes(indexes: Set<Int>, activity: Activity) {
        synchronized(loaderList) {
            var isFile = false
            indexes.forEach {
                if (File(loaderList[it].list.filePath).exists()) {
                    isFile = true
                    return@forEach
                }
            }
            if (isFile) {
                with(activity) {
                    AlertDialog.Builder(activity)
                            .setTitle(this@with.getString(R.string.delete) + "?")
                            .setPositiveButton(R.string.delete_with_files) { _, _ ->
                                removesSome(indexes, true)
                            }
                            .setNegativeButton(R.string.remove_from_list) { _, _ ->
                                removesSome(indexes, false)
                            }
                            .setNeutralButton(" ", null)
                            .show()
                }
            } else {
                removesSome(indexes, false)
            }
        }
    }

    private fun removesSome(indexes: Set<Int>, withFile: Boolean) {
        val delLoader: MutableList<Downloader> = mutableListOf()
        //save deleted items and delete from queue
        indexes.forEach {
            stop(it)
            delLoader.add(loaderList[it])
        }

        //remove loaders
        delLoader.forEach {
            it.waitEnd()
            Saver.removeList(it.list)
            loaderList.remove(it)
            if (withFile) {
                Storage.getDocument(it.list.filePath).delete()
                if (it.list.subsUrl.isNotEmpty())
                    Storage.getDocument(File(File(it.list.filePath).parent, it.list.title + ".srt").canonicalPath)?.delete()
            }
        }

        //shift queue list
        synchronized(lockQueue) {
            indexes.forEach {
                for (i in 0 until queueList.size)
                    if (queueList[i] > it)
                        queueList[i] -= delLoader.size
            }
        }
    }

    fun removeAll(activity: Activity) {
        synchronized(loaderList) {
            var isFile = false
            loaderList.forEach {
                if (File(it.list.filePath).exists()) {
                    isFile = true
                    return@forEach
                }
            }
            if (isFile) {
                with(activity) {
                    AlertDialog.Builder(activity)
                            .setTitle(this@with.getString(R.string.delete_all_items) + "?")
                            .setPositiveButton(R.string.delete_with_files) { _, _ ->
                                stopAll()
                                loaderList.forEach {
                                    it.waitEnd()
                                    Saver.removeList(it.list)
                                    Storage.getDocument(it.list.filePath).delete()
                                    if (it.list.subsUrl.isNotEmpty())
                                        Storage.getDocument(File(File(it.list.filePath).parent, it.list.title + ".srt").canonicalPath).delete()
                                }
                                loaderList.clear()
                            }
                            .setNegativeButton(R.string.remove_from_list) { _, _ ->
                                stopAll()
                                loaderList.forEach {
                                    it.waitEnd()
                                    Saver.removeList(it.list)
                                }
                                loaderList.clear()
                            }
                            .setNeutralButton(" ", null)
                            .show()
                }
            } else {
                stopAll()
                loaderList.forEach {
                    it.waitEnd()
                    Saver.removeList(it.list)
                }
                loaderList.clear()
            }
        }
    }

    fun saveLists() {
        try {
            loaderList.forEach { Saver.saveList(it.list) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /////////Queue funcs
    private val lockQueue: Any = Any()
    private var loading: Boolean = false
    private var currentLoader = -1

    fun isLoading(): Boolean {
        loaderList.forEach {
            if (it.isLoading())
                return true
        }
        return false
    }

    fun load(index: Int) {
        if (index in 0 until loaderList.size) {
            if (loaderList[index].isComplete())
                return
            if (!inQueue(index))
                queueList.add(index)
            synchronized(lockQueue) {
                thread { startLoading() }
            }
        }
    }

    fun loadAll() {
        loaderList.forEachIndexed { index, downloader ->
            if (!downloader.isComplete() && !inQueue(index))
                queueList.add(index)
        }
        synchronized(lockQueue) {
            thread { startLoading() }
        }
    }

    fun stop(index: Int) {
        loaderList[index].stop()
        if (queueList.contains(index))
            synchronized(lockQueue) {
                queueList.remove(index)
            }
    }

    fun stopAll() {
        synchronized(lockQueue) {
            queueList.clear()
            loaderList.forEach { it.stop() }
            currentLoader = -1
        }
    }

    private fun startLoading() {
        synchronized(lockQueue) {
            if (queueList.isEmpty())
                return
            if (loading)
                return
            loading = true
        }
        thread {
            LoaderService.start()
            currentLoader = -1
            while (queueList.size > 0) {
                if (!loading)
                    break
                var loader: Downloader? = null
                synchronized(lockQueue) {
                    currentLoader = queueList[0]
                    if (currentLoader in 0 until loaderList.size)
                        loader = loaderList[currentLoader]
                    thread { loader?.load() }
                    queueList.removeAt(0)
                }
                loader?.let {
                    Thread.sleep(100)
                    it.waitEnd()
                    if (currentLoader != -1 && it.getState().state == LoadState.ST_ERROR) {
                        loading = false
                        queueList.clear()
                    }
                    Saver.saveList(it.list)
                }
            }
            loading = false
            currentLoader = -1
            LoaderService.stop()
            saveLists()
        }
    }

    fun inQueue(index: Int): Boolean {
        synchronized(lockQueue) {
            if (index == currentLoader)
                return true
            if (index in 0 until loaderList.size)
                queueList.forEach { if (it == index) return true }
            return false
        }
    }

    fun getCurrentLoader(): Int {
        return currentLoader
    }
}