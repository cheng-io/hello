<template>
  <Abcd></Abcd>
</template>
<script lang="ts">
import Abcd from './index2.vue'
import {defineComponent} from "vue";
export default defineComponent({
  name: 'QIJIANSUANYIJIEZHUAN',
  components:{
    Abcd
  }
})

</script>
<style>
.pingZhengPageContainer {
  position: absolute;
  border-radius: 4px;
  min-width: 1010px;
  top: 10px;
  width: calc(100% - 20px);
  left: 10px;
  height: calc(100% - 30px);
  overflow: hidden;
}
</style>
