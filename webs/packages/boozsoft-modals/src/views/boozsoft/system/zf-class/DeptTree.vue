<template>
  <div class="bg-white mr-0 overflow-hidden">
    <div style="width: 100%;height: 50px;">
      <label style="font-size: 20px;line-height: 50px;margin-left: 20px;">政府支出经济</label>
      <div style="float: right;margin-right: 20px;margin-top: 10px;">
        <a style="cursor: pointer;color: blue;" @click="openAddPage()">
          <PlusOutlined/>
          添加</a>
      </div>
    </div>
    <BasicTree
      title=""
      :click-row-to-expand="false"
      :tree-data="treeData"
      :replace-fields="{ key: 'id', title: 'value' }"
      v-model:selectedKeys="selectedKeys"
      v-model:expandedKeys="expandedKeys"
      @select="handleSelect"
    />
    <DeptEdit
      @save="saveData"
      @register="registerEditPage"
    />
  </div>

</template>
<script setup lang="ts">
import {computed, defineExpose, onMounted, ref, watch} from 'vue'

import {BasicTree} from '/@/components/Tree'
import DeptEdit from './popup/daptEdit.vue'
import {useModal} from '/@/components/Modal'

import {
  PlusOutlined
} from '@ant-design/icons-vue'
import {saveDept, GetDeptTreeByFlag, getDeptListById} from '/@/api/record/system/zf-class'
import {getCurrentAccountName} from "/@/api/task-api/tast-bus-api";
import {useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";

const emit = defineEmits(['select'])
const treeData: any = ref([])
const selectedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])
const props = defineProps(['modelValue'])
// 默认账套编码
let defalutCompany = getCurrentAccountName(true)
// 切换后账套编码
const dynamicCompany = ref(defalutCompany)
// 默认账套编码
let defalutCompanyCodeValue = ''

async function fetch(accId) {
  if(accId){
    dynamicCompany.value = accId
  }
  let deptTree = await useRouteApi(GetDeptTreeByFlag,{schemaName: dynamicCompany.value})({})
  console.log(deptTree)
  function a(deptTree: any) {
    deptTree.forEach((item: any) => {
      if (item.children != null) {
        a(item.children)
      }
      item.value = '[' + item.ecCode + ']  ' + item.ecName
    })
  }

  function b(deptTree: any) {
    let arr: any = []
    arr.push(deptTree[0].id)
    return arr
  }

  a(deptTree)
  // deb
  treeData.value = []
  treeData.value.push({id:'0',key:'',value: companyName,children: deptTree})
  // treeData.value.push(...deptTree)
  // treeData.value = (await getDeptList())
  // let checks = b(deptTree)
  let checks = b(treeData.value)
  selectedKeys.value = checks
  expandedKeys.value = checks
  emit('select', checks[0])
}

function handleSelect(keys: string, e: any) {
  let keyStr = keys[0]+',';
  if (null != e.selectedNodes && e.selectedNodes.length > 0
    && null != e.selectedNodes[0].children &&  e.selectedNodes[0].children.length > 0){
    keyStr = getThisNodeStr(e.selectedNodes[0].children,keyStr)
  }
  emit('select', keyStr.substring(0,keyStr.length-1))
  // emit('select', keys[0])
  // console.log(keys, e)
}

function getThisNodeStr(list,str) {
  for (const nods of list) {
    str+=(nods.id+',')
    if (null != nods.children && nods.children.length > 0){
      str = getThisNodeStr(nods.children,str)
    }
  }
  return str
}

const val = {
  id: null,
  uniqueCode: '',
  deptCode: '',
  deptName: '',
  parentId: '',
  uniqueCodeUser: '',
  check: ''
}
const [registerEditPage, {openModal: openEditPage}] = useModal()
const openAddPage = () => {
  val.check = selectedKeys.value[0]
  openEditPage(true, {
    data: val,
    dynamicTenantId: dynamicCompany.value
  })
}

onMounted(async () => {
  await fetch()
})

async function saveData(data: any) {
  await useRouteApi(saveDept, {schemaName: dynamicCompany.value})(data)
  await fetch()
}

const totalChange = computed(() => props.modelValue.total)
let companyName = computed(() => props.modelValue.thisAdInfo.companyName)
watch(companyName, async () => {
    let a = props.modelValue.thisAdInfo
    companyName = props.modelValue.thisAdInfo.companyName
    if (Object.keys(a).length > 0) {
      dynamicCompany.value = a.accountMode
      // dynamicCompany.value = a.accId + '-' + defalutCompany.split('-')[2]
      await fetch()
    }
  }
)
defineExpose({fetch})
</script>
<style scoped>
.bg-white {
  width: 250px !important;
  min-height: 250px !important;
  height: calc(100% - 170px);
  border: 1px #cccccc solid;
  background: white !important;
  margin-right: .2%;
}
</style>
