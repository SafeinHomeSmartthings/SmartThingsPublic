/**
 *  SiH_Hello_World
 *
 *  Copyright 2018 Nick Thompson
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
    name: "Safeinhome_IoT_Router",
    namespace: "safeinhome",
    author: "Nick Thompson",
    description: "Safeinhome ST router for the new OTS system.",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Enter Provisioning Code") {
		input "provisionCode", "text"
	}

	section("Select Door Sensors") {
		input "contact", "capability.contactSensor", multiple: true, required: false
	}

    section("Select Motion Sensors") {
    	input "motion", "capability.motionSensor", multiple: true, required: false
    }

    section("Select Smoke Alarm") {
        input "smoke", "capability.smokeDetector", multiple: true, required: false
    }

    section("Select CO Alarm") {
        input "monoxide", "capability.carbonMonoxideDetector", multiple: true, required: false
    }

    section("Select Tamper-Enabled Sensors") {
        input "tamper", "capability.tamperAlert", multiple: true, required: false
    }

    section("Select High Temp sensors") {
        input "highTemp", "capability.temperatureMeasurement", multiple: true, required: false
    }
    
    section("Select Bed/Chair Pad") {
    	input "pressurePad", "capability.contactSensor", multiple: true, required: false
    }
    
    section("Select Pill Box") {
    	input "pillBox", "capability.contactSensor", multiple: true, required: false
    }

    section("Select all devices") {
        input "provisionSensors", "capability.sensor", multiple: true, required: true
    }

    section("Should these devices be (re)provisioned?") {
        input "shouldProvision", "bool", required: true
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(contact, "contact", contactHandler)
    subscribe(motion, "motion", motionHandler)
    subscribe(smoke, "smoke", smokeHandler)
    subscribe(monoxide, "carbonMonoxide", monoxideHandler)
    subscribe(tamper, "tamper", tamperHandler)
    subscribe(pressurePad, "contact", pressurePadHandler)
    subscribe(pillBox, "contact", pillBoxHandler)

    //

	if(shouldProvision) {
    	//provision
    	def provMess = ProvisioningMessage(location.hubs[0].id, provisionSensors, provisionCode)
    	sendProvisioningDataToSiH(provMess)
    }

    //initialize accumulatedSensorData variable
    atomicState.accumulatedSensorData = new ArrayList<String>()
    runEvery1Minute(sendDataToSiH)
    //Send with sensor Heartbeats
    runEvery10Minutes(sendHeartbeatToSiH, [data: true])
    //Send without sensor heartbeats
    runEvery15Minutes(sendHeartbeatToSiH, [data: false])
    //Send temperature Data
    runEvery10Minutes(addTempDataToAccumulation)
    runEvery10Minutes(addBedPadStatusToAccumulation)
}

def StringFromBool(boolVal) {
    if (boolVal == true) {
        return "True"
    }
    else {
        return "False"
    }
}

def contactHandler(evt) {
    def isOpen = StringFromBool(evt.value == "closed")

    def accumulatedSensorData = atomicState.accumulatedSensorData
    accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "DOOR", new Date(), isOpen, evt.getDevice().batteryState.value, "False"))
    atomicState.accumulatedSensorData = accumulatedSensorData
}

def motionHandler(evt) {
    if(evt.value == "active") {
        def accumulatedSensorData = atomicState.accumulatedSensorData
        accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "MOTION", new Date(), "True", evt.getDevice().batteryState.value, "False"))
        atomicState.accumulatedSensorData = accumulatedSensorData
    }
}

def smokeHandler(evt) {
    def accumulatedSensorData = atomicState.accumulatedSensorData
    accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "SMOKE", new Date(), evt.value, evt.getDevice().batteryState.value, "False"))
    atomicState.accumulatedSensorData = accumulatedSensorData
}

def monoxideHandler(evt) {
    def accumulatedSensorData = atomicState.accumulatedSensorData
    accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "MONOXIDE", new Date(), evt.value, evt.getDevice().batteryState.value, "False"))
    atomicState.accumulatedSensorData = accumulatedSensorData
}

def tamperHandler(evt) {
    if(evt.value == "detected") {
        log.debug "TAMPER DETECTED"
        def accumulatedSensorData = atomicState.accumulatedSensorData
        accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "TAMPER", new Date(), "True", evt.getDevice().batteryState.value, "False"))
        atomicState.accumulatedSensorData = accumulatedSensorData
    }
}

def pressurePadHandler(evt) {
    def isOpen = StringFromBool(evt.value == "closed")
    
    log.debug evt.value
    
    def accumulatedSensorData = atomicState.accumulatedSensorData
    accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "BED_PRESENCE", new Date(), isOpen, "100", "False"))
    atomicState.accumulatedSensorData = accumulatedSensorData
}

def pillBoxHandler(evt) {
    def eventValue = 0
    
    if(evt.value == "open") {
    	eventValue = 0
        log.debug "FIRE ON"
    } else {
    	eventValue = 64
        log.debug "FIRE OFF"
    }

	def accumulatedSensorData = atomicState.accumulatedSensorData
    accumulatedSensorData.add(SensorMessage(evt.getDevice().id, evt.getDevice().label, "PILLS", new Date(), eventValue.toString(), "100", "False"))
    atomicState.accumulatedSensorData = accumulatedSensorData
}

def addTempDataToAccumulation() {
    def accumulatedSensorData = atomicState.accumulatedSensorData
    
    //get temperature scale
    def tempScale = location.getTemperatureScale()
    
    provisionSensors.each { device ->
        if(device.hasCapability("Temperature Measurement")) {
        	
            def tempState = device.temperatureState.getDoubleValue() + tempScale
            
            accumulatedSensorData.add(SensorMessage(device.id, device.label, "TEMPERATURE", new Date(), tempState, device.batteryState.value, "False"))
        }
    }

    atomicState.accumulatedSensorData = accumulatedSensorData;
}

def addBedPadStatusToAccumulation() {
    def accumulatedSensorData = atomicState.accumulatedSensorData
    
    pressurePad.each { device ->
        def isOpen = StringFromBool(device.contactState == "closed")

        accumulatedSensorData.add(SensorMessage(device.id, device.label, "BED_PRESENCE", new Date(), isOpen, "100", "True"))
    }

    atomicState.accumulatedSensorData = accumulatedSensorData
}

def ProvisioningMessage(String hubId, ArrayList sensorList, String provisionCode) {
    def sensorListToProvision = []

    sensorList.each { device -> 
        def types = device.capabilities
        sensorListToProvision.add([uuid: device.id, name: device.label, type: device.name])
    }
    
    def provisioningMessage = [
        name: location.hubs[0].name,
        uuid: hubId,
        locationName: location.name,
        sensors: sensorListToProvision,
        provisionCode: provisioniCode
    ]

    return provisioningMessage
}

def GatewayMessage(String hubId) {
    def gatewayMessage = [
        gatewayID: hubId,
        gatewayName: location.hubs[0].name,
        accountId: "",
        networkId: externNetworkCode,
        messageType: "0",
        power: "0",
        messageDate: getDateString(new Date()),
        count: 0,
        signalStrength: "100",
        pendingChange: "False"
    ]
    return gatewayMessage
}

def SensorMessage(String senId, String senName, String sType, Date msgDt, String data, String batteryLevel, String heartbeat) {
    def sensorMessage = [
        sensorID: senId,
        sensorName: senName,
        neworkID: "",
        dataMessageGUID: UUID.randomUUID().toString(),
        messageDate: getDateString(msgDt),
        rawData: data,
        dataValue: data,
        dataType: sType,
        plotValues: "",
        plotLabels: "",
        batteryLevel: batteryLevel.toString(),
        signalStrength: "100",
        pendingChange: "False",
        isHeartbeat: heartbeat
    ]
    
    log.debug groovy.json.JsonOutput.toJson(sensorMessage)

    return sensorMessage
}

def getDateString(Date dt) {
    return dt.format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("UTC"))
}

def makeSQSBody(String bodyText) {
    return "Action=SendMessage&MessageBody=$bodyText"
}

//Endpoint - string without backslash
//Data - json object to send
def sendDataToSiH() {
    def sensorData = atomicState.accumulatedSensorData

    if(sensorData.size() == 0) {
        log.debug "sensorData is empty, skipping send"
        return
    }

    def gatewayData = GatewayMessage(location.hubs[0].id)

    for (def i = 0; i < sensorData.size() ; i++ ) {
        sensorData[i].put("count", Integer.toString(sensorData.size()))
    }

    def msgData = [
        gatewayMessage: gatewayData,
        sensorMessages: sensorData
    ]
    
    def dataJson = groovy.json.JsonOutput.toJson(msgData)

    log.debug "data: $dataJson"

	def params = [
    	path: "/804478468145/SiH_IoT_Router",
        body: makeSQSBody(dataJson),
        uri: "https://sqs.us-east-2.amazonaws.com/",
        contentType: "application/x-www-form-urlencoded"
	]

    log.debug params
	try {
        httpPost(params) { resp ->
            log.debug "resp: $resp"
            if(resp.getStatus() == 200) {
                def newList = new ArrayList<String>()
                atomicState.accumulatedSensorData = newList;

                log.debug("atomicState cleared: ${atomicState.accumulatedSensorData}")
            }
        }
    } catch (e) {
    	e.printStackTrace()
	}
}

def sendProvisioningDataToSiH(Map messageData) {
    log.debug messageData
    
    def dataJson = groovy.json.JsonOutput.toJson(messageData)

    log.debug "Provisioning Data:\n"
    log.debug dataJson

	def params = [
    	path: "/api/Provision",
        body: dataJson,
        uri: "http://apipub.safeinhome.com/",
        contentType: "application/json"
	]

	try {
        httpPost(params) { resp ->
            log.debug "resp: " + resp.getData()
        }

	} catch (e) {
    	log.debug e
	}
}

def sendHeartbeatToSiH(Boolean withSensorHeartbeat) {
    def gatewayData = GatewayMessage(location.hubs[0].id)

    def msgData = [
        gatewayMessage: gatewayData,
        sensorMessages: []
    ]

    if(withSensorHeartbeat) {
        gatewayMessage["sensorMessages"] = getHeartbeatSensorMessages(provisionSensors)
    }

    def dataJson = groovy.json.JsonOutput.toJson(msgData)

	def params = [
    	path: "/804478468145/SiH_IoT_Router",
        body: makeSQSBody(dataJson),
        uri: "https://sqs.us-east-2.amazonaws.com/",
        contentType: "application/x-www-form-urlencoded"
	]

    log.debug params
	try {
        httpPost(params) { resp ->
            log.debug "resp: $resp"
        }

	} catch (e) {
    	e.printStackTrace()
	}
}

def getHeartbeatSensorMessages(ArrayList sensorList) {
    def sensorMessages = []

    sensorList.each { device -> 
        def hasBattery = false

        def caps = device.capabilities
        caps.each { cap -> 
            if(cap.name == "Battery") {
                hasBattery = true
            }
        }

        def batteryState = "100"

        if(hasBattery) {
            batteryState = device.batteryState.value
        }

        (String senId, String senName, String sType, Date msgDt, String data, String batteryLevel, String heartbeat)

        sensorMessages.add(SensorMessage(device.id, device.label, "HEARTBEAT", new Date(), "True", batteryState, "True"))
    }

    return sensorMessages
}