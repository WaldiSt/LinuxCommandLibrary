package com.inspiredandroid.linuxcommandbibliotheca.models

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Required
import java.util.*

/**
 * Created by Simon Schubert
 */
open class CommandChildModel : RealmObject() {

    @Required
    var command: String? = null
    var mans: RealmList<CommandManModel> = RealmList()

    fun getMansAsStringArray(): Array<String> {
        return mans.map { it.man!! }.toTypedArray()
    }
}