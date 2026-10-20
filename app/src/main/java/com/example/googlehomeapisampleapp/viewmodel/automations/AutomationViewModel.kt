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

package com.example.googlehomeapisampleapp.viewmodel.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.home.automation.Action
import com.google.home.automation.Automation
import com.google.home.automation.ManualStarter
import com.google.home.automation.Node
import com.google.home.automation.ParallelFlow
import com.google.home.automation.SelectFlow
import com.google.home.automation.SequentialFlow
import com.google.home.automation.Starter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AutomationViewModel(var automation: Automation) : ViewModel() {

  val id: MutableStateFlow<String> = MutableStateFlow(automation.id.id)
  val name: MutableStateFlow<String> = MutableStateFlow(automation.name)
  val description: MutableStateFlow<String> = MutableStateFlow(automation.description)
  val isActive: MutableStateFlow<Boolean> = MutableStateFlow(automation.isActive)
  val isValid: MutableStateFlow<Boolean> = MutableStateFlow(automation.isValid)
  val nodes: MutableStateFlow<List<Node>>

  val isManuallyExecutable: StateFlow<Boolean>
  val starters: MutableStateFlow<List<Starter>>
  val actions: MutableStateFlow<List<Action>>

  init {
    nodes = MutableStateFlow(retrieveNodes(automation.automationGraph!!))

    isManuallyExecutable =
      nodes
        .map { nodes -> nodes.any { it is ManualStarter } }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000L),
          initialValue = nodes.value.any { it is ManualStarter }
        )

    // Initialize starters and actions by parsing nodes:
    starters = MutableStateFlow(retrieveStarters(nodes.value))
    actions = MutableStateFlow(retrieveActions(nodes.value))
  }

  fun retrieveNodes(node: Node): List<Node> {
    // Container for all nodes discovered so far:
    val discoveredNodes: MutableList<Node> = mutableListOf(node)
    // Container for all child nodes to search:
    val childNodes: List<Node> =
      when (node) {
        is SequentialFlow -> node.nodes
        is ParallelFlow -> node.nodes
        is SelectFlow -> node.nodes
        else -> emptyList()
      }
    // Add the results from child nodes recursively:
    for (childNode in childNodes) {
      discoveredNodes.addAll(retrieveNodes(childNode))
    }
    // Return all discovered nodes:
    return discoveredNodes
  }

  fun retrieveStarters(nodes: List<Node>): List<Starter> {
    // Container for all starter nodes:
    val starterNodes: MutableList<Starter> = mutableListOf()
    // Extract nodes that are starters:
    for (node in nodes)
      if (node is Starter)
        starterNodes.add(node)
    // Return the starter nodes:
    return starterNodes
  }

  fun retrieveActions(nodes: List<Node>): List<Action> {
    // Container for all action nodes:
    val actionNodes: MutableList<Action> = mutableListOf()
    // Extract nodes that are actions:
    for (node in nodes)
      if (node is Action)
        actionNodes.add(node)
    // Return the action nodes:
    return actionNodes
  }

}