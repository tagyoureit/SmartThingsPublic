/**
 *  Luxor ZDC Controller
 *
 *  Copyright 2018 Russell Goldin
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
metadata {
	definition (name: "Luxor ZDC Controller", namespace: "tagyoureit", author: "Russell Goldin") {
		capability "Light"
	}


	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
        multiAttributeTile(name:"light", type: "generic", width: 1, height: 1, canChangeIcon: true)  {

            tileAttribute("device.light", key: "PRIMARY_CONTROL") {
                attributeState "off", label: '${name}', action: "light.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: ""           
                attributeState "on", label: '${name}', action: "light.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: ""
            }
        }
    }
}

def installed() {
    log.debug "executing ZDC Controller installed"
    manageChildren()

}

// parse events into attributes
def parse(physicalgraph.device.HubResponse hubResponse) {
	log.debug "Parsing '${hubResponse}'"
	// TODO: handle 'switch' attribute
    log.debug "Switch response is ${hubResponse.json}"

}

def parseAllOn(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Light response is ${hubResponse.json}"
    if (hubResponse.json.Status==0){
    	log.info "All Luxor Lights turned on."
        sendEvent(name: "Light", value: "on", displayed:true) 
    }
    else {
    	log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

def parseAllOff(physicalgraph.device.HubResponse hubResponse) {
    if (hubResponse.json.Status==0){
    	log.info "All Luxor Lights turned off."
        sendEvent(name: "light", value: "off", displayed:true) 
    }
    else {
    	log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

// handle commands
def off() {
	log.debug "Executing 'off'"
    allOff()
}

def on() {
	log.debug "Executing 'on'"
    allOn()
}

def updated() {
  log.debug "Executing ZDC Controller updated"
  unsubscribe()
  manageChildren()
}

def controllerHubGet(def apiCommand, def body="{}", def _callback) {
	def controllerIP = getDataValue('controllerIP')
    
    def cb = [:]
    if (_callback) {
    	cb["callback"] = _callback
    }
    def result = new physicalgraph.device.HubAction(
        method: "POST",
        path: apiCommand,
        body: "${body}",
        headers: [
            "HOST" : "$controllerIP:80",
            "Content-Type": "application/json"],
        //getDataValue("controllerMac"),
        null,
        cb
    )
    log.debug result.toString()
    sendHubCommand(result);
}

def manageChildren() {
	log.debug "manage children in ZDC Controller"
	controllerHubGet("/GroupListGet.json", null, "parseGroupListGet")
}

def parseGroupListGet(physicalgraph.device.HubResponse hubResponse) {
    log.debug "GroupListGet response: ${hubResponse.json}"
    def hub = location.hubs[0]
    def hubId = hub.id
    def groups = hubResponse.json.GroupList
    def groupType
    def devices = getChildDevices()
    groups.each{group ->
        log.debug "group $group"

        if (getDataValue("controllerType")=="ZDC"){
            def childMac = "${hubResponse.mac}-${group.Name}-${group.Grp}".replaceAll("\\s","")
            log.debug "comparing $childMac to all devices"
           	 devices.each {
                log.debug "my DIN is $it.deviceNetworkId.  It is a match: ${childMac==it.deviceNetworkId}"
            } 
            def device = devices.find {
                childMac == it.deviceNetworkId
            }
            log.debug "0. device is found??? $device"
            if (device){

                log.debug "verified a device???"
                device.updateDataValue("controllerIP", getDataValue("controllerIP"))
               // device.value << [verified: true]  <-- Need this??
               // update device values here.
            }
            else {
                log.debug "1.  no device found? $device"
                if (group.Colr==0){
                    groupType = "Monochrome"
                }
                else {
                    groupType = "Color"      

                }
                def lightGroup = "Luxor $groupType Group"
                def componentName = "${group.Name}componentname".replaceAll("\\s","")
                log.info "Creating Luxor $groupType light group #:${group.Grp}, name ${group.Name}, Intensity ${group.Inten}, Color ${group.Colr}"

                log.debug "2.  values? for $lightGroup  $childMac, $hubId"
				log.debug "3.  getDataValue(controllerIP) ${getDataValue('controllerIP')}    getDataValue(controllerPort)  ${getDataValue('controllerPort')}"
                def params = ["label"         : "${group.Name} basic label",
                              "completedSetup": true,
                              "data"          : [
                                  "intensity": group.Inten,
                                  "color" : group.Colr,
                                  "group" : group.Grp,
                                  "controllerType"	 : "ZDC",
                                  "controllerIP": getDataValue("controllerIP"),
                                  "controllerPort": getDataValue("controllerPort")

                              ],
                              "isComponent": false, 
                              "componentName": componentName, 
                              "componentLabel": "${group.Name} group label"
                             ]
                log.debug "4.  params are $params"     
				log.debug "5.  about to add child with values namespace: tagyoureit \n  lightgroup $lightGroup \n childMac $childMac \n hubId $hubId \n params: $params"
                device = addChildDevice("tagyoureit", lightGroup, childMac, hubId, params)
                log.debug "6.  Light Group Added $device"

            }
        }
        else {
            log.debug "in else statement"
        }
    }
}

def refresh(){
	manageChildren()
}

def allOn() {
    controllerHubGet('/IlluminateAll.json', null, "parseAllOn")
}

def allOff() {
    controllerHubGet('/ExtinguishAll.json', null, "parseAllOff")
}

// called from Children
def childRefresh(){
	log.debug "childRefresh() called"
	manageChildren()
}

