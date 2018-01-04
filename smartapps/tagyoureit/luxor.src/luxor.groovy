/**
*  Luxor
*
*  Copyright 2017 Russell Goldin
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*/
definition(
    name: "Luxor",
    namespace: "tagyoureit",
    author: "Russell Goldin",
    description: "SmartApp to control Luxor ZD and ZDC Lighting Controllers by Hunter",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

def debug() {
    // set to false to disable logging
    return true
}

preferences {
        section("Luxor ZD/ZDC Setup") {
            input "luxorIP",title: "Enter IP Address of Luxor Controller", required: true
        }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    //subscribe(location, null, parse, [filterEvents:false])
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def hubGet(def apiCommand, def body="{}", def _callback) {
	def cb = [:]
    if (_callback) {
    	cb["callback"] = _callback
    }
    def result = new physicalgraph.device.HubAction(
        method: "POST",
        path: apiCommand,
        body: "${body}",
        headers: [
            "HOST" : "${luxorIP}:80",
            "Content-Type": "application/json"],
        null,
        cb
    )
    log.debug cb
    log.debug result.toString()
    sendHubCommand(result);

}

// gets the address of the Hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddress() {
    def ip = settings.ip //getDataValue("ip")
    def port = 80 //getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

def parseControllerName(physicalgraph.device.HubResponse hubResponse) {
    log.debug "ControllerName response: ${hubResponse.json}"
    if (hubResponse.json.Controller.toLowerCase().contains("lxzdc")){
        log.info "Discovered LXZDC controller at $luxorIP"
        state.controllerType = "ZDC"
    }
    else {
        log.info "Discovered LXZD controller at $luxorIP"
        state.controllerType = "ZD"
    }

    addControllerAsDevice(hubResponse.mac)
    
}

def addControllerAsDevice(mac){
    def hub = location.hubs[0]
    def d = getChildDevices()?.find { it.deviceNetworkId == mac}
    if (d) {
        d.updateDataValue("controllerIP", luxorIP)
        //d.value << [name: body?.device?.roomName?.text(), model: body?.device?.modelName?.text(), serialNumber: body?.device?.serialNum?.text(), verified: true]
        //d.value << [name: body?.device?.roomName?.text(), model: body?.device?.modelName?.text(), serialNumber: body?.device?.serialNum?.text(), verified: true]
        //d.value << [verified: true]
        d.manageChildren()
    } else {
        log.info "Creating Luxor ${state.controllerType} Controller Device with dni: ${mac}"
        d = addChildDevice("tagyoureit", "Luxor ${state.controllerType} Controller", mac, hub.id,
                           ["label"         : "Luxor ${state.controllerType} Controller",
                            "completedSetup": true,
                            "data"          : [
                                "controllerMac"     : mac,
                                "controllerIP"      : luxorIP,
                                "controllerPort"    : 80,
                                "controllerType"	: state.controllerType
                            ]

                           ])

    }
    log.debug "Controller Device is $d"

}

def controllerTypeEnum() {

    return [
        "ZDC":["Group":"Grp",
               "Intensity": "Inten"
              ]
    ]

}

def getChildDNI(name) {
    return mac + "-" + name
}


def initialize() {

    state.luxorIP = settings.luxorIP
    log.debug "Initializing Luxor Controller"
    hubGet("/ControllerName.json", null, "parseControllerName")

}





def check(){

}