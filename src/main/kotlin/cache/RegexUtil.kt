package org.burgas.cache

class RegexUtil {

    companion object {
        val PHONE: Regex = Regex("^\\+[1-9]\\d{6,14}$")
        val MESSENGER: Regex = Regex("@[a-zA-Z0-9_]{4,32}")
        val EMAIL: Regex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}