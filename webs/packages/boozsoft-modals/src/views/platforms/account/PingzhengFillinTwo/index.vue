
<template>
  <div>
    <PingzhengIndex />
  </div>
</template>
<script lang="ts">
import PingzhengIndex from '/@/components/pingzheng-fillin-two/index.vue';
import { defineComponent, onActivated, provide } from 'vue';

let fun = null;
function asda() {
  provide('onActivedFun', (fun1) => {
    fun = fun1;
  });
}
export default defineComponent({
  name: 'TianZhiPingZhengTwo',
  components: {
    PingzhengIndex,
  },
  setup() {
    asda();
  },
  activated() {
    if (fun != null) {
      fun();
    }
  },
  created() {},
});
</script>
<style>
.pingZhengPageContainer {
  border-radius: 4px;
  min-width: 1010px;
  width: calc(100% - 20px);
  height: calc(100% - 30px);
  overflow: hidden;
}
</style>
