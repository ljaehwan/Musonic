package app.musonic.settings

import android.content.Context
import androidx.annotation.StringRes

/** Keeps messages locale-independent until they are displayed. */
data class UiText(@StringRes val resource: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resource, *args.toTypedArray())
}
fun uiText(@StringRes resource: Int, vararg args: Any) = UiText(resource, args.toList())
class LocalizedInputException(val text: UiText) : IllegalArgumentException("Invalid server configuration")
