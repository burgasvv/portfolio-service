package org.burgas.cache

import org.burgas.database.DatabaseFactory

class CacheUtil {

    companion object {
        val REDIS = DatabaseFactory.REDIS
        const val IDENTITY_KEY = "identityFullResponse::%s"
        const val PORTFOLIO_KEY = "portfolioFullResponse::%s"
        const val PROFESSION_KEY = "professionFullResponse::%s"
    }
}