<template>
  <div style="background:gray;width:100px;height:100px;">
    <div>
      <div v-show="currentPluginName==='modals'"><modals></modals></div>
      <div v-show="currentPluginName==='print'"><Print></Print></div>
<!--      <div v-else>未选择模块</div>-->
    </div>
  </div>
</template>
<script setup>

import Modals from '../../views/SubApps/PluginApps/Modals/index.vue'
import Print from '../../views/SubApps/PluginApps/Print/index.vue'

import {usePluginsStoreWidthOut} from "../../store/modules/plugins";
import {computed} from "vue";

const currentPluginName=computed(()=>usePluginsStoreWidthOut().getCurrentPluginName)
</script>