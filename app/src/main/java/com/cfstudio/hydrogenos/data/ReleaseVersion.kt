package com.cfstudio.hydrogenos.data

/** Compare numeric Android version codes; never infer update ordering from display strings. */
internal fun isNewerVersionCode(remoteVersionCode: Int, installedVersionCode: Int): Boolean =
    remoteVersionCode > installedVersionCode
