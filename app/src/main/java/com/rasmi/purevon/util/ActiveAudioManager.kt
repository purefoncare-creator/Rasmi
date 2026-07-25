package com.rasmi.purevon.util

object ActiveAudioManager {
    
    private var activePlayer: AudioPlayer? = null
    
    @Synchronized
    fun setActive(player: AudioPlayer) {
        val previous = activePlayer
        if (previous != null && previous !== player) {
            previous.pause()
        }
        activePlayer = player
    }
    
    @Synchronized
    fun clearIfActive(player: AudioPlayer) {
        if (activePlayer === player) {
            activePlayer = null
        }
    }
}
