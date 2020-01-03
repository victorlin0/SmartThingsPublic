/**
 *  Copyright 2019 Victor Lin
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
 *
 *
 *
 */
 
import groovy.json.JsonOutput

metadata {
	definition(name: "Tosmota Window Shade", namespace: "victorlin0", author: "Victor Lin", ocfDeviceType: "oic.d.blind", mnmn: "SmartThings", vid: "generic-shade") {
		capability "Actuator"
        capability "Switch"
		capability "Configuration"
		capability "Refresh"
		capability "Window Shade"

		attribute "lastActivity", "string"
        attribute "OpenButton", "string"
        attribute "CloseButton", "string"

		command "pause"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"windowShade", type: "generic", width: 6, height: 4) {
			tileAttribute("device.windowShade", key: "PRIMARY_CONTROL") {
				attributeState "open",   label: 'Open',   action: "close", icon: "http://www.ezex.co.kr/img/st/window_open.png", backgroundColor: "#00A0DC", nextState: "closing"
				attributeState "closed", label: 'Closed', action: "open", icon: "http://www.ezex.co.kr/img/st/window_close.png", backgroundColor: "#ffffff", nextState: "opening"
				attributeState "partially open", label: 'Partially open', action: "close", icon: "http://www.ezex.co.kr/img/st/window_open.png", backgroundColor: "#d45614", nextState: "closing"
				attributeState "opening", label: 'Opening', action: "pause", icon: "http://www.ezex.co.kr/img/st/window_open.png", backgroundColor: "#00A0DC", nextState: "partially open"
				attributeState "closing", label: 'Closing', action: "pause", icon: "http://www.ezex.co.kr/img/st/window_close.png", backgroundColor: "#ffffff", nextState: "partially open"
			}
		}
		standardTile("contPause", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "pause", label:"", icon:'st.sonos.pause-btn', action:'pause', backgroundColor:"#cccccc"
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		valueTile("shadeLevel", "device.level", width: 4, height: 1) {
			state "level", label: 'Shade is ${currentValue}% up', defaultState: true
		}
		controlTile("levelSliderControl", "device.level", "slider", width:2, height: 1, inactiveLabel: false) {
			state "level", action:"switch level.setLevel"
		}

		main "windowShade"
		details(["windowShade", "contPause", "shadeLevel", "levelSliderControl", "refresh"])
	}

   	preferences {
	
    	section("Sonoff Host") {
	    	input(name: "ipAddress", type: "string", title: "IP Address", description: "IP Address of Sonoff", displayDuringSetup: true, required: true)
		    input(name: "port", type: "number", title: "Port", description: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
	
	    }

		section("Authentication") {
			input(name: "username", type: "string", title: "Username", description: "Username", displayDuringSetup: false, required: false)
			input(name: "password", type: "password", title: "Password (sent cleartext)", description: "Caution: password is sent cleartext", displayDuringSetup: false, required: false)
		}
	}

}

def parse(String description) {
	def message = parseLanMessage(description)	//def message = parseLanMessage(testLegacyInput())

	// parse result from current and legacy formats
	def resultJson = {}
	if (message?.json) {
		// current json data format
		resultJson = message.json
	}
	else {
		// legacy Content-Type: text/plain
		// with json embedded in body text
		def STATUS_PREFIX = "STATUS = "
		def RESULT_PREFIX = "RESULT = "
		if (message?.body?.startsWith(STATUS_PREFIX)) {
			resultJson = new groovy.json.JsonSlurper().parseText(message.body.substring(STATUS_PREFIX.length()))
		}
		else if (message?.body?.startsWith(RESULT_PREFIX)) {
			resultJson = new groovy.json.JsonSlurper().parseText(message.body.substring(RESULT_PREFIX.length()))
		}
	}

	// consume and set switch state
	if ((resultJson?.POWER in ["ON", 1, "1"]) || (resultJson?.Status?.Power in [1, "1"])) {
	  	log.debug "parses() switch state is ON"
		setSwitchState(true)
	}
	else if ((resultJson?.POWER in ["OFF", 0, "0"]) || (resultJson?.Status?.Power in [0, "0"])) {
	  	log.debug "parses() switch state is OFF"
		setSwitchState(false)
	}
	else {
		log.error "can not parse result with header: $message.header"
		log.error "...and raw body: $message.body"
	}
}

def setSwitchState(Boolean on) {
	log.info "setSwitchState() " + (on ? "ON" : "OFF")
	sendEvent(name: "switch", value: on ? "on" : "off")
}

def push() {
	log.debug "PUSH"
	sendCommand("Power1", "1")
//    runIn(5, refresh, [overwrite: true])	//Force a sync with tilt sensor after 20 seconds

}

def on() {
	log.debug "Open"
	sendCommand("Power2", "1")
//    runIn(5, refresh, [overwrite: true])	//Force a sync with tilt sensor after 20 seconds
}

def open() {
	log.debug "open"
	sendCommand("Power2", "1")
}


def off() {
	log.debug "Close"
	sendCommand("Power1", "1")
}

def close() {
	log.debug "Close"
	sendCommand("Power1", "1")
}


def poll() {
	log.debug "POLL"
	sendCommand("Status", null)
}

def refresh() {
	log.debug "REFRESH"
	sendCommand("Status", null)
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	log.debug "PING"
	sendCommand("Status", "8")
}

def pause() {
	log.debug "pause"
	sendCommand("Power3", "1")
}
private def sendCommand(String command, String payload) {

	if (!ipAddress || !port) {
		log.warn "aborting. ip address or port of device not set"
		return null;
	}
	def hosthex = convertIPtoHex(ipAddress)
	def porthex = convertPortToHex(port)
	device.deviceNetworkId = "$hosthex:$porthex"

	def path = "/cm"
	if (payload){
		path += "?cmnd=${command}%20${payload}"
	}
	else{
		path += "?cmnd=${command}"
	}

	if (username){
		path += "&user=${username}"
		if (password){
			path += "&password=${password}"
		}
	}

    log.debug "HTTP GET ${ipAddress}:${port}${path}"
	def result = new physicalgraph.device.HubAction(
		method: "GET",
		path: path,
		headers: [
			HOST: "${ipAddress}:${port}"
		]
	)
   	log.debug "sendCommand(${command}:${payload}) to device at $ipAddress:$port"
	return result
}

private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format('%04x', port.toInteger())
	return hexport
}




