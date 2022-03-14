package com.espero.yaade.server

import com.espero.yaade.db.DaoManager
import com.espero.yaade.server.routes.CollectionRoute
import com.espero.yaade.server.routes.RequestRoute
import com.espero.yaade.server.routes.UserRoute
import com.espero.yaade.server.routes.health
import com.espero.yaade.server.utils.authorizedCoroutineHandler
import com.espero.yaade.server.utils.coroutineHandler
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.openapi.RouterBuilder
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await


class Server(private val port: Int, private val daoManager: DaoManager) : CoroutineVerticle() {

    private val collectionRoute = CollectionRoute(daoManager)
    private val requestRoute = RequestRoute(daoManager)
    private val userRoute = UserRoute(daoManager)

    override suspend fun start() {
        val store = LocalSessionStore.create(vertx)
        val provider = LocalAuthProvider(daoManager)
        val routerBuilder = RouterBuilder.create(vertx, "src/main/resources/openapi.yaml").await()
        routerBuilder.bodyHandler(BodyHandler.create())
        routerBuilder.rootHandler(SessionHandler.create(store))

        routerBuilder.operation("health").coroutineHandler(this, ::health)

        routerBuilder.operation("doLogin").coroutineHandler(this, AuthHandler(provider))

        routerBuilder.operation("getCurrentUser")
            .authorizedCoroutineHandler(this, userRoute::getCurrentUser)
        routerBuilder.operation("changeUserPassword")
            .authorizedCoroutineHandler(this, userRoute::changePassword)

        routerBuilder.operation("getAllCollections")
            .authorizedCoroutineHandler(this, collectionRoute::getAllCollections)
        routerBuilder.operation("postCollection")
            .authorizedCoroutineHandler(this, collectionRoute::postCollection)
        routerBuilder.operation("putCollection")
            .authorizedCoroutineHandler(this, collectionRoute::putCollection)
        routerBuilder.operation("deleteCollection")
            .authorizedCoroutineHandler(this, collectionRoute::deleteCollection)

        routerBuilder.operation("postRequest")
            .authorizedCoroutineHandler(this, requestRoute::postRequest)
        routerBuilder.operation("putRequest")
            .authorizedCoroutineHandler(this, requestRoute::putRequest)
        routerBuilder.operation("deleteRequest")
            .authorizedCoroutineHandler(this, requestRoute::deleteRequest)

        val router = routerBuilder.rootHandler(LoggerHandler.create(LoggerFormat.DEFAULT)).createRouter()

        router.route("/api/logout").handler(userRoute::logout)

        router.route("/*").coroutineHandler(this, StaticHandler.create());

        val server = vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .await()

        println("Started server on port ${server.actualPort()}")
    }
}