package com.aura.core

enum class DeviceKind { PHONE, CAMERA, DEPTH, LIDAR, SENSOR, WINDOWS_PC, LINUX_NODE, ARDUINO, VEHICLE, ROBOT, UNKNOWN }

data class DeviceDescriptor(val id: String, val name: String, val kind: DeviceKind, val capabilities: Set<String> = emptySet())

interface AuraDeviceAdapter {
    val descriptor: DeviceDescriptor
    fun connect(): Boolean
    fun disconnect()
}

class DeviceRegistry {
    private val devices = linkedMapOf<String, DeviceDescriptor>()
    fun register(device: DeviceDescriptor) { devices[device.id] = device }
    fun find(id: String) = devices[id]
    fun all() = devices.values.toList()
}
