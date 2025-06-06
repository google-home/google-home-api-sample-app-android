
/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.viewmodel.settings
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.home.Descriptor
import com.google.home.DeviceType
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.HomeObjectsFlow
import com.google.home.Structure
import com.google.home.Trait
import com.google.home.automation.Action
import com.google.home.automation.Automation
import com.google.home.automation.BinaryExpression
import com.google.home.automation.Comprehension
import com.google.home.automation.Condition
import com.google.home.automation.Constant
import com.google.home.automation.ExpressionWithId
import com.google.home.automation.FieldSelect
import com.google.home.automation.ListContains
import com.google.home.automation.ListGet
import com.google.home.automation.ListIn
import com.google.home.automation.ListSize
import com.google.home.automation.ManualStarter
import com.google.home.automation.Node
import com.google.home.automation.ParallelFlow
import com.google.home.automation.Reference
import com.google.home.automation.SelectFlow
import com.google.home.automation.SequentialFlow
import com.google.home.automation.Starter
import com.google.home.automation.StateReader
import com.google.home.automation.TernaryExpression
import com.google.home.automation.UnaryExpression
import com.google.home.automation.UnknownExpression
import com.google.home.google.ExtendedColorControl
import com.google.home.google.LightEffects
import com.google.home.matter.standard.BasicInformation
import com.google.home.matter.standard.ColorControl
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.OnOff
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.firstOrNull

private const val TAG = "SampleApp:Debugger"

@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "unused", "ConstantConditionIf")
class Debugger private constructor(homeClientRef: HomeClient) : ViewModel() {

    private val homeClient: HomeClient = homeClientRef

    companion object {
        @Volatile
        private var INSTANCE: Debugger? = null
        private val initialized = AtomicBoolean(false)

        fun getInstance(homeClientRef: HomeClient): Debugger {
            if (initialized.getAndSet(true)) {
                return INSTANCE!!
            }
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = Debugger(homeClientRef)
                }
                return INSTANCE!!
            }
        }
    }

    // ============================================================================================
    // Automation API
    // ============================================================================================

    private fun dumpAutomationCondition(condition: Condition) {
        Log.d(TAG, "====== Dump automationGraph Condition $condition")
        condition.apply {
            Log.d(TAG, "======= Dump automationGraph Condition.nodeId: ${this.nodeId}")
            Log.d(TAG, "======= Dump automationGraph Condition.forDuration: ${this.forDuration}")
        }
        val expression = condition.expression
        Log.d(TAG, "======= Dump automationGraph Condition.expression: $expression")
        when (expression) {
            is Constant -> {
                Log.d(TAG, "========= Dump automationGraph Constant: ${expression.constant}")
            }
            is ExpressionWithId -> {
                Log.d(TAG, "========= Dump automationGraph ExpressionWithId: ")
                when (expression) {
                    is BinaryExpression -> {
                    }
                    is Comprehension -> {
                    }
                    is FieldSelect -> {
                    }
                    is ListContains -> {
                    }
                    is ListGet -> {
                    }
                    is ListIn -> {
                    }
                    is ListSize -> {
                    }
                    is TernaryExpression -> {
                    }
                    is UnaryExpression -> {
                    }
                    is UnknownExpression -> {
                    }
                    else -> {
                    }
                }
                Log.d(TAG, "========= Dump automationGraph ExpressionWithId: Done")
            }
            is Reference -> {
                Log.d(TAG, "========= Dump automationGraph Reference: ${expression.reference}")
            }
            else -> {
                Log.d(TAG, "========= Dump automationGraph Condition: Unknown type $expression")
            }
        }
        Log.d(TAG, "======= Dump automationGraph expression: Done")
        //
        Log.d(TAG, "====== Dump automationGraph Condition: Done")
    }
    private suspend fun dumpAutomationNode(node: Node) {
        if (true) Log.d(TAG, "===== Dump automationGraph.node: Start")
        Log.d(TAG, "====== Dump automationGraph.node.nodeId: ${node.nodeId}")
        when (node) {
            is Action -> {
                Log.d(TAG, "====== Dump automationGraph Action $node")
                node.apply {
                    Log.d(TAG, "======= Dump automationGraph Action.nodeId: ${this.nodeId}")
                    Log.d(TAG, "======= Dump automationGraph Action.entity: ${this.entity}")
                    Log.d(TAG, "======= Dump automationGraph Action.deviceType: ${this.deviceType}")
                    Log.d(TAG, "======= Dump automationGraph Action.behavior: ${this.behavior}")
                }
                Log.d(TAG, "====== Dump automationGraph Action: Done")
            }
            is Condition -> {
                dumpAutomationCondition(node)
            }
            is ManualStarter -> {
                Log.d(TAG, "====== Dump automationGraph ManualStarter: $node")
                Log.d(TAG, "======= Dump automationGraph ManualStarter.nodeId: ${node.nodeId}")
                Log.d(TAG, "====== Dump automationGraph ManualStarter: Done")
            }
            is Starter -> {
                node.apply {
                    Log.d(TAG, "====== Dump automationGraph Starter.nodeId: ${this.nodeId}")
                    Log.d(TAG, "====== Dump automationGraph Starter.entity: ${this.entity}")
                    Log.d(TAG, "====== Dump automationGraph Starter.trait: ${this.trait}")
                    Log.d(TAG, "====== Dump automationGraph Starter.deviceType: ${this.deviceType}")
                    Log.d(TAG, "====== Dump automationGraph Starter.event: ${this.event}")
                    Log.d(TAG, "====== Dump automationGraph Starter.parameters: ${this.parameters}")
                    this.parameters.forEach { parameter ->
                        Log.d(TAG, "====== Dump automationGraph Starter.parameter: $parameter")
                        parameter.apply {
                            Log.d(TAG, "====== Dump automationGraph Starter.param: ${this.param}")
                            Log.d(TAG, "====== Dump automationGraph Starter.param.first: ${this.param.first}")
                            Log.d(TAG, "====== Dump automationGraph Starter.param.first.tag: ${this.param.first.tag}")
                            Log.d(TAG, "====== Dump automationGraph Starter.param.first.typeEnum: ${this.param.first.typeEnum}")
                            Log.d(TAG, "====== Dump automationGraph Starter.param.first.typeName: ${this.param.first.typeName}")
                            Log.d(TAG, "====== Dump automationGraph Starter.param.first.descriptor: ${this.param.first.descriptor}")
                            Log.d(TAG, "====== Dump automationGraph Starter.param.second: ${this.param.second}")
                        }
                    }
                    Log.d(TAG, "====== Dump automationGraph Starter.traitId: ${this.traitId}")
                    Log.d(TAG, "====== Dump automationGraph Starter.output: ${this.output}")
                }
            }
            is StateReader -> {
                node.apply {
                    Log.d(TAG, "====== Dump automationGraph StateReader.nodeId: ${this.nodeId}")
                    Log.d(TAG, "====== Dump automationGraph StateReader.entity: ${this.entity}")
                    Log.d(TAG, "====== Dump automationGraph StateReader.trait: ${this.trait}")
                    Log.d(TAG, "====== Dump automationGraph StateReader.deviceType: ${this.deviceType}")
                    Log.d(TAG, "====== Dump automationGraph StateReader.traitId: ${this.traitId}")
                    Log.d(TAG, "====== Dump automationGraph StateReader.output: ${this.output}")
                }
            }
            is ParallelFlow -> {
                dumpAutomationParallelFlow(node)
            }
            is SelectFlow -> {
                dumpAutomationSelectFlow(node)
            }
            is SequentialFlow -> {
                dumpAutomationSequentialFlow(node)
            }
            else -> {
                Log.d(TAG, "====== Dump automation.automationGraph.node: Unknown type: $node")
            }
        }
        Log.d(TAG, "====== Dump automationGraph.node.nodeId: Done")
        Log.d(TAG, "===== Dump automationGraph node: Done")
    }
    private suspend fun dumpAutomationParallelFlow(parallelFlow: ParallelFlow) {
        Log.d(TAG, "===== Dump automationGraph SequentialFlow child nodes: $parallelFlow")
        parallelFlow.nodes.forEach { node ->
            dumpAutomationNode(node)
        }
        Log.d(TAG, "===== Dump automationGraph SequentialFlow child nodes: Done")
    }
    private suspend fun dumpAutomationSelectFlow(selectFlow: SelectFlow) {
        Log.d(TAG, "===== Dump automationGraph child SelectFlow child nodes: $selectFlow")
        selectFlow.nodes.forEach { node ->
            dumpAutomationNode(node)
        }
        Log.d(TAG, "===== Dump automationGraph child SelectFlow child nodes: Done")
    }
    private suspend fun dumpAutomationSequentialFlow(sequentialFlow: SequentialFlow) {
        Log.d(TAG, "===== Dump automationGraph SequentialFlow child nodes: $sequentialFlow")
        sequentialFlow.nodes.forEach { node ->
            dumpAutomationNode(node)
        }
        Log.d(TAG, "===== Dump automationGraph SequentialFlow child nodes: Done")
    }
    private suspend fun dumpAutomation(automation: Automation) {
        //
        Log.i(TAG, "=== Dump automation: $automation")
        automation.apply {
            Log.d(TAG, "==== Dump automation.name: ${this.name}")
            Log.d(TAG, "==== Dump automation.id: ${this.id}")
            Log.d(TAG, "==== Dump automation.description: ${this.description}")
            Log.d(TAG, "==== Dump automation.compatibleWithSdk: ${this.compatibleWithSdk}")
            Log.d(TAG, "==== Dump automation.isValid: ${this.isValid}")
            Log.d(TAG, "==== Dump automation.validationIssues: ${this.validationIssues}")
            Log.d(TAG, "==== Dump automation.manuallyExecutable: ${this.manuallyExecutable}")
            Log.d(TAG, "==== Dump automation.isActive: ${this.isActive}")
            Log.d(TAG, "==== Dump automation.isRunning: ${this.isRunning}")
        }
        // Note: the first child container of an Automation is always an SequentialFlow
        val automationGraphSequentialFlow: SequentialFlow? = automation.automationGraph
        Log.d(TAG, "==== Dump automation.automationGraph: $automationGraphSequentialFlow")
        if (automationGraphSequentialFlow != null) {
            Log.d(TAG, "==== Dump automation.automationGraph.nodeId: ${automationGraphSequentialFlow.nodeId}")
            val automationNodes: List<Node> = automationGraphSequentialFlow.nodes
            automationNodes.forEach { node ->
                dumpAutomationNode(node)
            }
            Log.d(TAG, "==== Dump automation.automationGraph: Done")
        }
        Log.d(TAG, "=== Dump automation: Done")
    }
    suspend fun dumpAutomationsInStructure(structure: Structure) {
        Log.i(TAG, "== Dump automations of structure: $structure")
        //
        if (true) { // dumpAutomation
            val automations: HomeObjectsFlow<Automation> = structure.automations()
            automations.list().forEach { automation ->
                dumpAutomation(automation)
            }
        }
        Log.i(TAG, "== Dump automations of the structure: Done")
    }
    suspend fun dumpAutomationsInStructure(structureName: String) {
        homeClient.structures().list().filter { structure ->
            structure.name == structureName
        }.forEach { structure ->
            dumpAutomationsInStructure(structure)
        }
    }
    // ============================================================================================
    // D&S API
    // ============================================================================================

    // TODO: to dump trait specific values by the trait type
    private fun dumpTraitByType(deviceType: DeviceType, trait: Trait) {
        // TODO: Also show SimplifiedTraits
        // TODO: Dump FeatureMap
        try {
            when (trait) {
                // Generic
                is Descriptor -> {
                }
                // com.google.home.google.*
                is ExtendedColorControl -> {
                }
                is LightEffects -> {
                }
                // com.google.home.matter.standard.*
                is BasicInformation -> {
                }
                is com.google.home.matter.standard.Descriptor -> {
                }
                is OnOff -> {
                }
                is LevelControl -> {
                }
                is ColorControl -> {
                }
                else -> {
                    //
                }
            }
        } catch (e: HomeException) {
            Log.w(TAG, "====== dumpTraitByType: unable to show ${trait.factory}, error: $e")
        }
    }
    private fun dumpTrait(deviceType: DeviceType, trait: Trait) {
        Log.d(TAG, "====== Dump Trait: $trait")
        Log.d(TAG, "====== Dump Trait metadata.sourceConnectivity: ${trait.metadata.sourceConnectivity}")
        // TODO: Subscribe Trait and show the updates
        if (true) {
            dumpTraitByType(deviceType, trait)
        }
    }
    private fun dumpDeviceType(deviceType: DeviceType) {
        Log.d(TAG, "==== Dump DeviceType: $deviceType")
        deviceType.apply {
            Log.d(TAG, "====== Dump DeviceType factory: ${this.factory}")
            Log.d(TAG, "====== Dump DeviceType metadata: ${this.metadata}")
            Log.d(TAG, "====== Dump DeviceType metadata.isPrimaryType: ${this.metadata.isPrimaryType }")
            Log.d(TAG, "====== Dump DeviceType metadata.sourceConnectivity: ${this.metadata.sourceConnectivity}")
        }
        // TODO: Subscribe DeviceType and show the updates
        //
        val traitSet = deviceType.traits()
        traitSet.forEach { trait ->
            dumpTrait(deviceType, trait)
        }
        Log.d(TAG, "==== Dump DeviceType: Done")
    }
    private suspend fun dumpDevice(device: HomeDevice) {
        Log.i(TAG, "=== Dump Device: $device")
        device.apply {
            Log.i(TAG, "==== Dump Device.id: ${this.id}")
            Log.i(TAG, "==== Dump Device.name: ${this.name}")
            Log.i(TAG, "==== Dump Device.structureId: $structureId")
            Log.i(TAG, "==== Dump Device.isMatterDevice: ${this.isMatterDevice}")
            Log.i(TAG, "==== Dump Device.roomId: $roomId")
            Log.i(TAG, "==== Dump Device.roomId: ${this.roomId}")
            Log.i(TAG, "==== Dump Device.sourceConnectivity ${device.sourceConnectivity}")
        }
        //
        val deviceTypesFlow = device.types()
        deviceTypesFlow.firstOrNull()?.forEach { deviceType ->
            //
            dumpDeviceType(deviceType)
        }
        Log.i(TAG, "=== Dump Device: Done")
    }
    suspend fun dumpStructure(structure: Structure) {
        Log.i(TAG, "== Dump Structure: $structure")
        val devicesFlow = structure.devices()
        devicesFlow.list().forEach { device ->
            dumpDevice(device)
        }
        Log.i(TAG, "== Dump Structure: Done")
    }
    suspend fun dumpStructure(structureName: String) {
        homeClient.structures().list().filter { structure ->
            Log.i(TAG, "== Dump Structure: structure.name = ${structure.name} , structureName = $structureName")
            structure.name == structureName
        }.forEach { structure ->
            dumpStructure(structure)
        }
    }

    suspend fun dumpAll(structureName: String) {
        //
        Log.i(TAG, "= Dump Structure And Automations ")
        //
        homeClient.structures().list().filter { structure ->
            structure.name == structureName
        }.forEach { structure ->
            if (true) dumpAutomationsInStructure(structure)
            if (true) dumpStructure(structure)
        }
        Log.i(TAG, "= Dump Structure And Automations: Done)")
    }
}

