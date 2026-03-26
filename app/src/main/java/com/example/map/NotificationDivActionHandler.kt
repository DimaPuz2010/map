package com.example.map

import android.widget.Toast
import com.yandex.div.core.DivActionHandler
import com.yandex.div.core.DivViewFacade
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivAction

class NotificationDivActionHandler(
    private val onMoveToPoint: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    private val onToggleCards: () -> Unit = {},
) : DivActionHandler() {
    override fun handleAction(action: DivAction, view: DivViewFacade, resolver: ExpressionResolver): Boolean {
        if (super.handleAction(action, view, resolver)) {
            return true
        }

        val uri = action.url?.evaluate(view.expressionResolver) ?: return false
        if (uri.scheme == "notification" && uri.authority == "show-toast") {
            val text = uri.getQueryParameter("text") ?: return false
            Toast.makeText(view.view.context, text, Toast.LENGTH_LONG).show()
            return true
        }

        if (uri.scheme == "map" && uri.authority == "move") {
            val lat = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return false
            val lon = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: return false
            onMoveToPoint(lat, lon)
            return true
        }

        if (uri.scheme == "app" && uri.authority == "toggle-cards") {
            onToggleCards()
            return true
        }

        return false
    }
}
