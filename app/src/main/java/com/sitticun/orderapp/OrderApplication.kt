package com.sitticun.orderapp

import android.app.Application
import com.sitticun.orderapp.data.api.SupabaseClient
import com.sitticun.orderapp.data.local.OrderDatabaseHelper
import com.sitticun.orderapp.data.local.PreferenceManager
import com.sitticun.orderapp.data.repository.*
import com.sitticun.orderapp.util.DeviceIdProvider
import com.sitticun.orderapp.util.NetworkMonitor

class OrderApplication : Application() {

    lateinit var preferenceManager: PreferenceManager
        private set
    lateinit var databaseHelper: OrderDatabaseHelper
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var deviceIdProvider: DeviceIdProvider
        private set
    lateinit var supabaseClient: SupabaseClient
        private set

    lateinit var authRepository: AuthRepository
        private set
    lateinit var productRepository: ProductRepository
        private set
    lateinit var customerRepository: CustomerRepository
        private set
    lateinit var orderRepository: OrderRepository
        private set
    lateinit var stockRepository: StockRepository
        private set
    lateinit var outboxRepository: OutboxRepository
        private set
    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferenceManager = PreferenceManager(this)
        databaseHelper = OrderDatabaseHelper(this)
        networkMonitor = NetworkMonitor(this)
        deviceIdProvider = DeviceIdProvider(this, preferenceManager)
        supabaseClient = SupabaseClient(preferenceManager)

        authRepository = AuthRepository(supabaseClient, preferenceManager)
        productRepository = ProductRepository(supabaseClient, databaseHelper, networkMonitor)
        customerRepository = CustomerRepository(supabaseClient, databaseHelper, networkMonitor, deviceIdProvider)
        orderRepository = OrderRepository(
            supabaseClient,
            databaseHelper,
            preferenceManager,
            productRepository,
            customerRepository,
            networkMonitor,
            deviceIdProvider
        )
        stockRepository = StockRepository(
            supabaseClient,
            databaseHelper,
            productRepository,
            networkMonitor,
            deviceIdProvider
        )
        outboxRepository = OutboxRepository(supabaseClient, databaseHelper, networkMonitor)
        updateRepository = UpdateRepository()
    }

    companion object {
        lateinit var instance: OrderApplication
            private set
    }
}
