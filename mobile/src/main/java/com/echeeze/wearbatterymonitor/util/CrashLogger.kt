package com.echeeze.wearbatterymonitor.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * アプリケーション全域での未キャッチ例外（クラッシュ）を自動的に捕捉し、
 * 内部ストレージ (`crash_log.txt`) へスタックトレースを保存・管理するユーティリティクラスです。
 */
object CrashLogger {

    private const val LOG_FILE_NAME = "crash_log.txt"
    private const val TAG = "CrashLogger"

    /**
     * 未キャッチ例外ハンドラーを初期化し、クラッシュログの自動保存を有効化します。
     *
     * @param context アプリケーションコンテキスト
     */
    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(context, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 例外オブジェクトからスタックトレースを抽出し、`crash_log.txt` へ追記保存します。
     *
     * @param context コンテキスト
     * @param thread クラッシュが発生したスレッド
     * @param throwable 発生した例外オブジェクト
     */
    private fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
            val timeString = sdf.format(Date())

            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTrace = stringWriter.toString()

            val logText = buildString {
                append("========================================\n")
                append("CRASH TIME: $timeString\n")
                append("THREAD: ${thread.name}\n")
                append("EXCEPTION: ${throwable.javaClass.name}: ${throwable.message}\n")
                append("STACK TRACE:\n")
                append(stackTrace)
                append("\n========================================\n\n")
            }

            val logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile.appendText(logText)
            Log.e(TAG, "Crash log successfully saved to ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    /**
     * 保存されているクラッシュログテキストを取得します。
     *
     * @param context コンテキスト
     * @return 保存されているログ文字列。ログが存在しない場合は null
     */
    fun getCrashLog(context: Context): String? {
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        return if (logFile.exists() && logFile.length() > 0) {
            logFile.readText()
        } else {
            null
        }
    }

    /**
     * 保存されているクラッシュログファイルを削除します。
     *
     * @param context コンテキスト
     * @return 削除が成功した場合は true
     */
    fun clearCrashLog(context: Context): Boolean {
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        return if (logFile.exists()) {
            logFile.delete()
        } else {
            true
        }
    }
}
