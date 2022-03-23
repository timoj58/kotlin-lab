package com.tabiiki.kotlinlab.service


interface NetworkControllerService {
    //TODO need to add things to display positions of items on network etc.
    //and send events to it etc....leave for now.
}

class NetworkControllerServiceImpl(
    private val networkService: NetworkService
) : NetworkControllerService {
    init {
        networkService.start()
    }
}