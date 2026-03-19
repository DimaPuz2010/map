package com.example.map

import android.widget.Toast
import com.yandex.div.core.DivActionHandler
import com.yandex.div.core.DivViewFacade
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivAction

class NotificationDivActionHandler : DivActionHandler() {
    override fun handleAction(action: DivAction, view: DivViewFacade, resolver: ExpressionResolver): Boolean {
        if (super.handleAction(action, view, resolver)) {
            return true
        }

        val uri = action.url?.evaluate(view.expressionResolver) ?: return false
        if (uri.authority != "show-toast" || uri.scheme != "notification") return false
        val text = uri.getQueryParameter("text") ?: return false

        Toast.makeText(view.view.context, text, Toast.LENGTH_LONG).show()
        return true
    }
}
