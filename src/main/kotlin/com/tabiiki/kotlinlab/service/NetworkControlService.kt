package com.tabiiki.kotlinlab.service


interface NetworkControlService {
    //TODO need to add things to display positions of items on network etc.
    //and send events to it etc....leave for now.
}

class NetworkControlServiceImpl(
    private val networkService: NetworkService
) : NetworkControlService {
    init {
        networkService.start()
    }
}