<template>
  <BasicModal
    width="500px"
    v-bind="$attrs"
    title="供应商分类"
    @ok="handleOk()"
    @register="register"
  >
    <template #title>
      <div style="display: flex;" class="vben-basic-title">
        <img src="/create.svg" style="width:25px;margin-right: 10px;"/>
        <span style="line-height: 25px;font-size: 16px;">供应商分类</span>
      </div>
    </template>
    <div
      class="nc-open-content"
      style="height: 100%"
    >
      <div class="open-content-up" style="text-align: center;margin-top: 30px;">
        <label style="font-size: 18px;margin-left: 0;">分类名称：</label>
        <a-input v-model:value="formItems.cusCclassName" placeholder="" class="abc" style="width: 65%;" />
        <span class="red_span">*</span>
        <br/><br/><br/>
        <label>分类编码：</label>
        <a-input v-model:value="formItems.cusClass" placeholder=""/>
        <span class="red_span">*</span>
        <br/><br/>

        <label>上级分类：</label>
        <TreeSelect
          v-model:value="formItems.parentId"
          style="width: 50%"
          :dropdown-style="{ maxHeight: '400px', overflow: 'auto' }"
          :tree-data="treeData"
          placeholder="请选择上级分类"
          tree-default-expand-all
          allow-clear
          @change="treeChange"
        >
        </TreeSelect>
        <span class="red_span"></span>
      </div>
    </div>
  </BasicModal>
</template>
<script setup="props, { content }" lang="ts">
  import { onMounted, reactive, ref, toRaw, unref } from 'vue';
  import { BasicModal, useModalInner } from '/@/components/Modal';
  import {
    GetCustomerClassTree,
    verifyCusClass,
    verifyCusClassName,
  } from '/@/api/record/system/supplier_class';
  import {
    TreeSelect,
    Form as AForm,
    Select as ASelect,
    Input as AInput,
    Statistic as AStatistic,
    message,
  } from 'ant-design-vue';
  import {getCurrentAccountName, hasBlank} from "/@/api/task-api/tast-bus-api";
  import {useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";
  import {useMessage} from "/@/hooks/web/useMessage";
  import {useUserStoreWidthOut} from "/@/store/modules/user";
  import {saveLog} from "/@/api/record/system/group-sys-login-log";
  import {findAllByUniqueCustclass} from "/@/api/record/supplier_data/supplier";
  const ASelectOption = ASelect.Option;
  const AInputSearch = AInput.Search;
  const AStatisticCountdown = AStatistic.Countdown;
  const AFormItem = AForm.Item;
  const {createConfirm, createWarningModal, createMessage,createErrorModal} = useMessage();
  const emit=defineEmits(['register']);
  const accountInfo:any = ref({})
  const formItems:any = ref({})
  const database = ref(getCurrentAccountName(true));
  const treeData = ref([]);

  // 数据库模式名称
  const [register, { closeModal }] = useModalInner( async (data) => {
    formItems.value.cusClass=''
    formItems.value.cusCclassName=''
    formItems.value.parentId=''
    accountInfo.value=data.accountInfo
    // 供应商信息传入
    if(data.database!==undefined){database.value=data.database}
    formItems.value.parentId = data.data.parentId;
    const aa=await useRouteApi(GetCustomerClassTree,{schemaName: database})('')
    function a(aa) {
      aa.forEach((item) => {
        if (item.children != null) {
          a(item.children);
        }
        item.title = '(' + item.cusClass + ')' + item.cusCclassName;
        item.value = item.uniqueCustclass;
        item.key = item.uniqueCustclass;
      });
    }
    a(aa);
    treeData.value=aa
  });


  async function handleOk() {
    if(formItems.value.cusCclassName===undefined || formItems.value.cusCclassName===''){
      return createErrorPop('分类名称不能为空！');
    }else{
        const sumName = await useRouteApi(verifyCusClassName,{schemaName: database})({parentId: formItems.value.parentId,cusClassName: formItems.value.cusCclassName});
        if (sumName > 0) {
          return createErrorPop('同级别分类名称已存在！');
        }
    }
    if(formItems.value.cusClass===undefined || formItems.value.cusClass===''){
      return createErrorPop('分类编码不能为空！');
    }else{
      const sumNum = await useRouteApi(verifyCusClass,{schemaName: database})(formItems.value.cusClass);
      if (sumNum > 0) {
        return createErrorPop('分类编码已存在！');
      }
    }
    formItems.value.parentId=formItems.value.parentId==undefined?'':formItems.value.parentId
    // 埋点-操作日志
    let log='操作内容【新增供应商分类】,账套代码【'+accountInfo.value.coCode+'】,账套名称【'+accountInfo.value.companyName+'】,分类编码【'+formItems.value.cusClass+'】,分类名称【'+formItems.value.cusCclassName+'】'
    /************** 记录操作日志 ****************/
    let logmap={
      loginTime:new Date( +new Date() + 8 * 3600 * 1000 ).toJSON().substr(0,19).replace("T"," "),
      userId: useUserStoreWidthOut().getUserInfo.username,
      userName: useUserStoreWidthOut().getUserInfo.realName,
      optModule:'master_data',
      optFunction:'供应商分类',
      optRange:'1',
      uniqueCode:database.value,
      optAction:'新增',
      optContent:log,
    }
    await saveLog(logmap)
    /************** 记录操作日志 ****************/
    console.log(log)
    emit('save', unref(formItems));
    closeModal();
  }
function createErrorPop(text) {
  createWarningModal({
    iconType: 'warning',
    title: '提示',
    content: text
  })
  return false
}

async function treeChange(val) {
  if(!hasBlank(val)){
   let data= await useRouteApi(findAllByUniqueCustclass,{schemaName: database})({uniqueCustclass:val});
   if(data.length>0){
      createConfirm({
        iconType: 'warning',
        title: '警告',
        content: '上级分类已有供应商档案,继续添加将自动修改成新分类下，确认要添加吗？',
        onOk: async () => {},
        onCancel: () => {
          formItems.value.parentId=""
          return false
        }
      })
    }
  }
}
</script>
<style lang="less" scoped>
:deep(.vben-basic-title) {
  color: rgb(1, 129, 226) !important;
}

:deep(.ant-select-single:not(.ant-select-customize-input)
.ant-select-selector
.ant-select-selection-search-input) {
  border: none !important;
}

.vben-basic-title {
  color: rgb(1, 129, 226) !important;
}

.ant-modal-body {
  padding: 0px;
  border: 1px solid rgb(1, 129, 226);
  border-left: none;
  border-right: none;
}

.red_span {
  color: red;
  font-weight: bold;
  display: inline-block;
  width: 20px;
  text-align: right;
}

.nc-open-content {
  input {
    width: 50%;
    border: none !important;
    border-bottom: 1px solid #bdb9b9 !important;
    font-weight: bold;
    font-size: 14px;
  }

  .abc{
    border-bottom: 2px solid #666666 !important;
    font-size: 18px;
  }

  .ant-input:focus {
    box-shadow: none;
  }

  :deep(.ant-select-selector) {
    border: none !important;
    border-bottom: 1px solid #bdb9b9 !important;
    font-weight: bold;
    font-size: 14px;
  }

  label {
    text-align: left;
    width: 110px;
    display: inline-block;
    padding-top: 5px;
    padding-bottom: 5px;
    color: #535353;
    font-size: 13px;
    margin-left: 1em;
    font-weight: bold;
  }

  .open-content-down {
    margin-top: 5%;

    .ant-tabs-tab-active::before {
      position: absolute;
      top: 0px;
      left: 0;
      width: 100%;
      border-top: 2px solid transparent;
      border-radius: 2px 2px 0 0;
      transition: all 0.3s;
      content: '';
      pointer-events: none;
      background-color: rgb(1, 143, 251);
    }
  }
}
</style>
