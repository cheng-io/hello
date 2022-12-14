package org.boozsoft.rest;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.boozsoft.domain.entity.*;
import org.boozsoft.domain.entity.account.ProjectCategory;
import org.boozsoft.domain.entity.account.ProjectItem;
import org.boozsoft.domain.entity.account.SysDepartment;
import org.boozsoft.domain.entity.account.SysPsn;
import org.boozsoft.domain.entity.codekemu.CodeKemu;
import org.boozsoft.domain.entity.share.project.base.Project;
import org.boozsoft.domain.entity.stock.StockSaleousing;
import org.boozsoft.domain.vo.SubjectInitialBalanceVo;
import org.boozsoft.domain.vo.VoucherBusCheckVo;
import org.boozsoft.repo.*;
import org.boozsoft.repo.accountInfo.AccountInfoRepository;
import org.boozsoft.repo.codekemu.CodeKemuRepository;
import org.boozsoft.repo.project.base.ProjectRepositoryBase;
import org.boozsoft.service.AccvoucherService;
import org.boozsoft.service.AccvoucherTemplateService;
import org.boozsoft.service.ProjectService;
import org.boozsoft.util.ReflectionUtil;
import org.boozsoft.util.XlsUtils3;
import org.boozsoft.utils.CollectOfUtils;
import org.springbooz.core.tool.result.R;
import org.springbooz.core.tool.utils.StringUtils;
import org.springbooz.datasource.r2dbc.service.R2dbcRouterLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple7;
import reactor.util.function.Tuple8;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @ClassName :
 * @Description : ????????????
 * @Author : miao
 * @Date: 2021-04-01 09:25
 */
@Slf4j
@RestController
@RequestMapping("/accvoucher")
@Api(value = "????????????????????????", tags = "API?????????????????????????????????")

public class AccvoucherController {

    @Autowired
    AccvoucherTemplateService accvoucherTemplateService;
    @Autowired
    SysDepartmentRepository departmentRepository;
    @Autowired
    SysPsnRepository psnRepository;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    SupplierRepository supplierRepository;
    @Autowired
    ProjectCategoryRepository projectCategoryRepository;
    @Autowired
    ProjectItemRepository projectItemRepository;
    @Autowired
    ProjectRepositoryBase projectRepository;
    @Autowired
    AccvoucherRepository accvoucherRepository;
    @Autowired
    AccvoucherDeleteRepository accvoucherDeleteRepository;
    @Autowired
    AccvoucherService service;
    @Autowired
    CodeKemuRepository codeKemuRepository;
    @Autowired
    ProjectService projectService;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    ProjectCashRepository projectCashRepository;

    @Autowired
    AccountInfoRepository accountInfoRepository;

    @Autowired
    KmCashFlowRepository kmCashFlowRepository;

    @Autowired
    FuzhuHesuanRepository fuzhuHesuanRepository;

    public static void main(String[] args) {
        Map map = new HashMap<>();
        map.put(1, null);
        List<Map> list = new ArrayList<>();
        list.add(map);
        System.out.println(JSON.toJSONString(list, SerializerFeature.WriteMapNullValue));
    }

    @Transactional
    @PostMapping("/importAccvoucher2") // ??????
    public Mono<R> listOCR(@RequestPart("file") FilePart filePartParm, @RequestPart("templateInfo") String templateInfo) throws Exception {
        /**
         * ????????????????????? ???
         */
        // ????????? -- ???????????? -- ??????????????? -- ???????????? -- ?????????????????????
        String[] parameter = templateInfo.split("--");
        AtomicReference<String[]> titlesAR = new AtomicReference(); // ????????????????????????
        AtomicReference<String> thisDbNameAR = new AtomicReference(); // ??????????????????
        AtomicReference<Boolean> checkPassed = new AtomicReference(true); // ??????????????????
        Path tempFilePath = Files.createTempFile("", new String(filePartParm.filename().getBytes("ISO-8859-1"), "UTF-8"));
        return Mono.just(filePartParm).flatMap(files -> taskRepository.save(new Task().setCaozuoUnique("test001").setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(DateUtil.thisMonth() + "").setMethod("??????")).map(entity1 -> files)).flatMap(filePart -> {
                    try {
                        return DataBufferUtils.write(filePart.content(), AsynchronousFileChannel.open(tempFilePath, StandardOpenOption.WRITE), 0).doOnComplete(() -> log.info("????????????")).collectList().map(item -> tempFilePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return Mono.just("");
                })// ?????????????????????
                // ??????????????????????????????
                .flatMap(item -> service.getTheHeaderOfTheCurrentlyImportedFile(parameter[0], parameter[1], parameter[2])).flatMap(item -> Mono.just(item.getT2()).doOnNext(tits -> {
                                    if (null == titlesAR.get()) titlesAR.set(new String[tits.size() - 1]);
                                    titlesAR.set(tits.toArray(new String[tits.size() - 1]));
                                })    // ????????????
                                .map(tits -> item.getT1().toArray(new String[tits.size() - 1]))              // ????????????
                )
                // ????????????excel??????
                .flatMap(titles -> { //??????????????????????????????
                    List<Object[]> list = null;
                    try {
                        list = new XlsUtils3(tempFilePath.toString()).getExcelObj(tempFilePath.toString(), titles, 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        assert tempFilePath != null;
                       /* try {
                            // Files.delete(tempFilePath);
                        } catch (IOException e) {
                            System.err.println("???????????????????????????????????????: ---------???---------");
                            e.printStackTrace();
                        }*/
                    }
                    return Mono.just(list);
                })
                // ??????????????????
                .flatMap(list -> {
                    Map mapArr = new HashMap();
                    mapArr.put("excellist", list);   // excel?????????
                    mapArr.put("error", "");
                    mapArr.put("code", "200");
                    if (null == list || list.size() == 0) {  // ??????????????????
                        mapArr.put("error", "???????????????????????????????????????????????????????????????");
                        mapArr.put("code", "404");
                        return Mono.just(mapArr);
                    }
                    // ????????????????????????
                    String[] systemTitleNames = titlesAR.get();
                    Set<String> projects = new HashSet<>();
                    int voucherNumberIndex = -1;
                    int dateIndex = -1;
                    int mdIndex = -1;
                    int mcIndex = -1;
                    String thisImportYearStr = "";
                    for (int i = 0; i < list.size(); i++) {
                        Object[] rows = list.get(i);
                        if (i == 0) { // ?????????????????????
                            for (int j = 0; j < rows.length; j++) {
                                if (systemTitleNames[j].equals("?????????")) voucherNumberIndex = j;
                                if (systemTitleNames[j].equals("????????????")) dateIndex = j;
                                if (systemTitleNames[j].equals("????????????")) mdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) mcIndex = j;
                            }
                        }
                        StringBuilder rowCheckMsg = new StringBuilder("");
                        int defalutSize = rows.length;
                        for (int j = 0; j < defalutSize; j++) {
                            if (systemTitleNames[j].equals("????????????") && rows[dateIndex] == "") {
                                rowCheckMsg.append("????????????" + rows[dateIndex] + " " + rows[voucherNumberIndex] + "???:????????????????????????!");
                            } else if (systemTitleNames[j].equals("????????????") && StrUtil.isNotBlank(rows[dateIndex].toString())) {
                                try {
                                    DateUtil.parseDate(rows[dateIndex].toString());
                                    if (i == 0) {
                                        thisImportYearStr = rows[dateIndex].toString().substring(0, 4);
                                    }
                                } catch (Exception e) {
                                    rowCheckMsg.append("????????????" + rows[dateIndex] + " " + rows[voucherNumberIndex] + "???:????????????????????????!");
                                }
                            }
                            // ???????????? ?????????
                            if (systemTitleNames[j].equals("?????????") && (null == rows[voucherNumberIndex] || rows[voucherNumberIndex] == "") /*&& parms[4].equals("1")*/) {
                                rowCheckMsg.append("???????????????????????????!");
                            }
                            // ????????????
                            if (systemTitleNames[j].equals("????????????") && rows[j] == "") {
                                rowCheckMsg.append("??????????????????????????????");
                            }
                         /*   if (systemTitleNames[j].equals("????????????")) {//????????????
                                if (rows[j] == "" || (!rows[j].equals("???") || !rows[j].equals("???"))) {
                                    rows[j] = "???";
                                }
                            }*/
                            // ?????????
                            if (systemTitleNames[j].equals("?????????") && rows[j] == "") {
                                rowCheckMsg.append("???????????????????????????");
                            }

                            if (systemTitleNames[j].equals("????????????")) {
                                BigDecimal mdB = new BigDecimal("0");
                                BigDecimal mcB = new BigDecimal("0");
                                if (systemTitleNames[j].equals("????????????") && rows[j] != "") {
                                    mdB = mdB.add(new BigDecimal(rows[j].toString()));
                                }
                                if (systemTitleNames[mcIndex].equals("????????????") && rows[j + 1] != "") {
                                    mcB = mcB.add(new BigDecimal(rows[mcIndex].toString()));
                                }
                                // ???????????????
                                if (mdB.doubleValue() == 0 && mcB.doubleValue() == 0 || (mdB.doubleValue() != 0 && mcB.doubleValue() != 0)) {
                                    rowCheckMsg.append("??????????????????????????????????????????????????????");
                                }
                            }
                            ListUtil.setOrAppend(list, i, ArrayUtil.setOrAppend(rows, defalutSize, rowCheckMsg.toString()));
                            if (checkPassed.get() && StrUtil.isNotBlank(rowCheckMsg.toString())) checkPassed.set(false);
                            if (systemTitleNames[j].equals("??????????????????") && null != rows[j] && NumberUtil.isNumber(rows[j].toString()) && (Integer.parseInt(rows[j].toString()) < 11)) {
                                projects.add(rows[j].toString());
                            }
                        }
                    }
                    String finalThisImportYearStr = thisImportYearStr;
                    return Mono.just("").map(s -> {
                        //?????????????????????????????????
                        Mono<HashSet<String[]>> all = codeKemuRepository.findAllByYear(finalThisImportYearStr).collectList().map(list1 -> new HashSet<>(getHashSetByKeMu(list1)));
                        Mono<HashSet<CodeKemu>> lastStage = codeKemuRepository.findAllByYearAndBend(finalThisImportYearStr).collectList().map(list1 -> new HashSet<>(list1));
                        // ??????????????????
                        Mono<List<SysPsn>> geSets = psnRepository.findAllPsnCodeOrPsnNameByFlag().collectList();
                        Mono<List<SysDepartment>> bmSets = departmentRepository.findAllDeptCodeOrDeptNameByFlag().collectList();
                        Mono<List<Customer>> khSets = customerRepository.findAllCustCodeOrCustNameByFlag().collectList();
                        Mono<List<Supplier>> gysSets = supplierRepository.findAllCustCodeOrCustNameByFlag().collectList();
                        Mono<List<Project>> proSets = projectRepository.findAllProjectCodeOrProjectNameByAll().collectList();
                                /*Mono<Map<String, Set<String>>> proMap = null;
                                if (projects.size() > 0) {
                                    proMap = Mono.just(new HashMap<String, Set<String>>()).map(maps ->
                                            Flux.fromIterable(projects)
                                                    .flatMap(proNum -> projectService.findByProjectCodeAndValue(proNum, parameter[2])
                                                            .doOnNext(sets -> maps.put(proNum, sets))
                                                    ).collectList().map(list1 -> maps)
                                    ).flatMap(a -> a);
                                } else {
                                    proMap = Mono.just(new HashMap<>());
                                }*/
                        // ????????????????????????
                        Mono<Map<String, Object>> xjMap = accountInfoRepository.findAll().collectList().flatMap(acclist -> {
                            if (acclist.size() > 0 && null != acclist.get(0)) {
                                thisDbNameAR.set(acclist.get(0).getAccCode());
                                // ????????????????????????
                                //return service.queryAccountByAccId(acclist.get(0).getAccCode()).map(entity -> (null != entity.getIcashFlow() && entity.getIcashFlow().equals("1")) ? true : false);
                                return Mono.just(false);
                            }
                            return Mono.just(false);
                        }).flatMap(isTrue -> {
                            HashMap<String, Object> maps = new HashMap<>();
                            maps.put("XJCheck", isTrue);
                            maps.put("XJList", new HashSet<String>());
                            if (isTrue) {
                                return projectCashRepository.findByProjectAllOrderByCode().collectList().map(list1 -> {
                                    maps.put("XJList", new HashSet<>(list1));
                                    return maps;
                                });
                            }
                            return Mono.just(maps);
                        });
                        return Mono.zip(all, lastStage, geSets, bmSets, khSets, gysSets, proSets, xjMap);
                    }).flatMap(zips -> zips.map(many -> {
                        mapArr.put("codeSets", many);
                        return mapArr;
                    }));
                })
                // ????????????????????????????????????????????????????????????????????????
                .flatMap(map -> {
                    List<Object[]> list = (List<Object[]>) map.get("excellist");
                    List<Map<String, Object>> ListMap = new ArrayList<>();
                    // ????????????????????????
                    String[] systemTitleNames = titlesAR.get();
                    int dateIndex = 0;
                    int numberIndex = 0;
                    int csignIndex = 0;
                    int subjectNumIndex = 0;
                    int subjectNameIndex = 0;
                    int ndSIndex = 0; //????????????
                    int ncSIndex = 0; //????????????
                    int unitPriceIndex = 0; //??????
                    int mdFIndex = 0; //????????????
                    int mcFIndex = 0; //????????????
                    int cdeptIdIndex = 0; //????????????
                    int cpersonIdIndex = 0; //????????????
                    int ccusIdIndex = 0; //????????????
                    int csupIdIndex = 0; //???????????????
                    int projectClassIdIndex = 0; //??????????????????
                    int projectIdIndex = 0; //????????????
                    int cashProjectIndex = 0; //????????????????????????
                    int nfratMdIndex = -1; //'??????????????????'
                    int nfratMcIndex = -1; //'??????????????????'
                    // ???????????????????????? ?????? ??? ?????? boo
                    Boolean fx = parameter[2].equals("1");
                    // ???????????? ??? ????????????
                    Tuple8<HashSet<String[]>, HashSet<CodeKemu>, List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, List<Project>, Map<String, Object>> beingSets = (Tuple8<HashSet<String[]>, HashSet<CodeKemu>, List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, List<Project>, Map<String, Object>>) map.get("codeSets");
                    for (int i = 0; i < list.size(); i++) {
                        Object[] row = list.get(i);
                        // ????????????????????????????????????
                        if (i == 0) {
                            for (int j = 0; j < row.length - 1; j++) {
                                if (systemTitleNames[j].equals("????????????")) dateIndex = j;
                                if (systemTitleNames[j].equals("????????????")) csignIndex = j;
                                if (systemTitleNames[j].equals("?????????")) numberIndex = j;
                                if (systemTitleNames[j].equals("????????????")) subjectNumIndex = j;
                                if (systemTitleNames[j].equals("????????????")) subjectNameIndex = j;
                                if (systemTitleNames[j].equals("????????????")) ndSIndex = j;
                                if (systemTitleNames[j].equals("????????????")) ncSIndex = j;
                                if (systemTitleNames[j].equals("??????")) unitPriceIndex = j;
                                if (systemTitleNames[j].equals("??????????????????")) nfratMdIndex = j;
                                if (systemTitleNames[j].equals("??????????????????")) nfratMcIndex = j;

                                if (systemTitleNames[j].equals("????????????")) mdFIndex = j;
//                        if (systemTitleNames[j].equals("????????????")) mcFIndex = j;

                                if (systemTitleNames[j].equals("????????????")) cdeptIdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) cpersonIdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) ccusIdIndex = j;
                                if (systemTitleNames[j].equals("???????????????")) csupIdIndex = j;
                                if (systemTitleNames[j].equals("??????????????????")) projectClassIdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) projectIdIndex = j;

                                if (systemTitleNames[j].equals("????????????????????????")) cashProjectIndex = j;
                            }
                        }
                        String codeErrorStr = "??????????????????" + row[dateIndex] + " " + row[numberIndex] + "??????????????????????????????????????????:";
                        Map<String, Object> mm = new HashMap<>();
                        String tDate = DateUtil.formatDate(DateUtil.parseDate(row[dateIndex].toString()));
                        mm.put("time", tDate + ">>" + row[numberIndex]);
                        ListMap.add(mm);
                        // ???????????????????????????????????????row[subjectNumIndex].toString()
                        Object codeValue = row[subjectNumIndex];
                        Map<String, String> mapArr = new HashMap<>();
                        StringBuilder rowCheckMsg = new StringBuilder(row[row.length - 1].toString());
                        if (null == codeValue || org.apache.commons.lang3.StringUtils.isBlank(codeValue.toString())) {
                            rowCheckMsg.append("?????????????????????????????????");
                        } else if (!checkImportKemuExist(beingSets.getT1(), codeValue, "2")) {
                            rowCheckMsg.append("?????????????????????????????????");
                        } else if (checkImportKemuExist(beingSets.getT1(), codeValue, "1")) {
                            rowCheckMsg.append("????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else {
                            HashSet<CodeKemu> kemuList = beingSets.getT2();
                            CodeKemu thisKemu = getThisKemu(codeValue, kemuList);
                            if (null == thisKemu) {
                                rowCheckMsg.append("," + codeErrorStr + codeValue + "???????????????????????????????????????????????????????????????????????????????????????????????????");
                            } else {
                                // ??????????????????
                                if ((boolean) beingSets.getT8().get("XJCheck") && (StringUtils.isNotBlank(thisKemu.getBcash()) || StringUtils.isNotBlank(thisKemu.getBbank()))) {
                                    String cash = setColValue(row, cashProjectIndex);
                                    Set<String> xjSets = (HashSet<String>) beingSets.getT8().get("XJList");
                                    if (StringUtils.isBlank(cash)) {
                                        rowCheckMsg.append(",???????????????????????????????????????");
                                    } else if (StringUtils.isNotBlank(cash) && !xjSets.contains(cash)) {
                                        rowCheckMsg.append(",?????????????????????????????????????????????????????????????????????????????????");
                                    }
                                }
                                // ??????????????????
                                if (thisKemu.getBdept().equals("1")) {
                                    String dValue = (null == row[cdeptIdIndex] ? "" : row[cdeptIdIndex].toString());
                                    List<SysDepartment> deptCollect = beingSets.getT4().stream().filter(item -> (fx ? item.getDeptName().equals(dValue) : item.getDeptCode().equals(dValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(dValue)) {
                                        rowCheckMsg.append(",??????" + (fx ? "??????" : "??????") + "?????????????????????");
                                    } else if (deptCollect.size() == 0) {
                                        rowCheckMsg.append(",??????" + (fx ? "??????" : "??????") + "??????" + dValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    } else {
                                        row[cdeptIdIndex] = deptCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBperson().equals("1")) {
                                    String grValue = (null == row[cpersonIdIndex] ? "" : row[cpersonIdIndex].toString());
                                    List<SysPsn> psnCollect = beingSets.getT3().stream().filter(item -> (fx ? item.getPsnName().equals(grValue) : item.getPsnCode().equals(grValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(grValue)) {
                                        rowCheckMsg.append(",??????" + (fx ? "??????" : "??????") + "?????????????????????");
                                    } else if (psnCollect.size() == 0) {
                                        rowCheckMsg.append(",??????" + (fx ? "??????" : "??????") + "??????" + grValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    } else {
                                        row[cpersonIdIndex] = psnCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBcus().equals("1")) {
                                    String cValue = (null == row[ccusIdIndex] ? "" : row[ccusIdIndex].toString());
                                    List<Customer> customerCollect = beingSets.getT5().stream().filter(item -> (fx ? (item.getCustName().equals(cValue) || item.getCustAbbname().equals(cValue)) : item.getCustCode().equals(cValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(cValue)) {
                                        rowCheckMsg.append(",??????" + (fx ? "??????" : "??????") + "?????????????????????");
                                    } else if (customerCollect.size() == 0) {
                                        rowCheckMsg.append(",??????" + (fx ? "??????" : "??????") + "??????" + cValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    } else {
                                        row[ccusIdIndex] = customerCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBsup().equals("1")) {
                                    String gyValue = (null == row[csupIdIndex] ? "" : row[csupIdIndex].toString());
                                    List<Supplier> supCollect = beingSets.getT6().stream().filter(item -> (fx ? (item.getCustName().equals(gyValue) || item.getCustAbbname().equals(gyValue)) : item.getCustCode().equals(gyValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(gyValue)) {
                                        rowCheckMsg.append(",?????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                    } else if (supCollect.size() == 0) {
                                        rowCheckMsg.append(",?????????" + (fx ? "??????" : "??????") + "??????" + gyValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    } else {
                                        row[csupIdIndex] = supCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBitem().equals("1")) {
                                    String pValue = (null == row[projectIdIndex] ? "" : row[projectIdIndex].toString());
                                    List<Project> proCollect = beingSets.getT7().stream().filter(item -> (fx ? (item.getProjectName().equals(pValue)) : item.getProjectCode().equals(pValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(pValue)) {
                                        rowCheckMsg.append(codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                    } else if (proCollect.size() == 0) {
                                        rowCheckMsg.append(codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + pValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    } else {
                                        row[projectIdIndex] = proCollect.get(0).getUniqueCode();
                                    }
                                }

                                row[subjectNameIndex] = thisKemu.getCcodeName(); // ??????????????????????????????????????????
                                if (!parameter[1].equals("1")) { // ?????????????????????
                                    continue;
                                }
                                if (thisKemu.getBnum().equals("1")) {//?????????????????? ?????? ??? ????????????
                                    String price = setColValue(row, unitPriceIndex);
                                    String jNum = setColValue(row, ndSIndex);
                                    String dNum = setColValue(row, ncSIndex);
                                    if (StringUtils.isBlank(price) || (StringUtils.isBlank(jNum) && StringUtils.isBlank(dNum)) || (StringUtils.isNotBlank(jNum) && StringUtils.isNotBlank(dNum))) {
                                        rowCheckMsg.append(",??????????????????,??????????????????????????????????????????????????????");
                                    } else if (StringUtils.isNotBlank(price) && (!NumberUtil.isNumber(price) || Double.parseDouble(price) < 0)) {
                                        rowCheckMsg.append(",???????????????????????????");
                                    } else if ((StringUtils.isNotBlank(jNum) && !NumberUtil.isNumber(jNum)) || (StringUtils.isNotBlank(dNum) && !NumberUtil.isNumber(dNum))) {
                                        rowCheckMsg.append(",??????????????????????????????????????????");
                                    }
                                }
                                if (thisKemu.getCurrency().equals("1")) {//???????????????????????? ?????? ??? ?????? ????????????
                                    String amountMd = setColValue(row, nfratMdIndex).replaceAll(",", "");
                                    String amountMc = setColValue(row, nfratMcIndex).replaceAll(",", "");
                                    String jNum = setColValue(row, mdFIndex);
                                    //String dNum = row[mcFIndex].toString();
                                    if ((StringUtils.isBlank(amountMd) && StringUtils.isBlank(amountMc)) || (StringUtils.isBlank(jNum) /*&& StringUtils.isBlank(dNum)) || (StringUtils.isNotBlank(jNum) && StringUtils.isNotBlank(dNum)*/)) {
                                        rowCheckMsg.append("," + "??????????????????????????????????????????");
                                    } else if ((StringUtils.isNotBlank(amountMd) && (!NumberUtil.isNumber(amountMd))) || (StringUtils.isNotBlank(amountMc) && (!NumberUtil.isNumber(amountMc))/* || Double.parseDouble(amount) < 0*/)) {
                                        rowCheckMsg.append("," + "????????????????????????????????????");
                                    } else if ((StringUtils.isNotBlank(jNum) && (!NumberUtil.isNumber(jNum)/* || Double.parseDouble(jNum) < 0*/)) /*|| (StringUtils.isNotBlank(dNum) && (!NumberUtil.isNumber(dNum) || Double.parseDouble(dNum) < 0))*/) {
                                        rowCheckMsg.append("," + "??????????????????????????????");
                                    }
                                }
                            }
                        }
                        ListUtil.setOrAppend(list, i, ArrayUtil.setOrAppend(row, row.length - 1, rowCheckMsg.toString()));
                        if (checkPassed.get() && StrUtil.isNotBlank(rowCheckMsg.toString())) checkPassed.set(false);
                    }
                    //
                    if (!checkPassed.get()) { // ?????????
                        String lastFilePath = WriteCheckInfoToExcel(tempFilePath.toString(), list, csignIndex);
                        return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(list).map(o -> R.ok().setResult(CollectOfUtils.mapof("pass", checkPassed.get(), "path", lastFilePath)));
                    } else {// ????????? ????????????
                        try {
                            Files.delete(tempFilePath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(list).map(o -> R.ok().setResult(CollectOfUtils.mapof("pass", checkPassed.get(), "path", "")));
                    }
                });

          /*//??????????????????list
          List<Map<Object, Object>> new_ListMap = new ArrayList<Map<Object, Object>>();
          Set arr = new HashSet();
          for (int i = 0; i < ListMap.size(); i++) {
            Map m = ListMap.get(i);
            if (arr.contains(m.get("time"))) {
              continue;
            }
            //??????key?????????set
            arr.add(m.get("time"));
            //??????????????????
            new_ListMap.add(m);
          }
          map.put("timelist", new_ListMap);
          map.put("kemuList", beingSets.getT2());
          // ???????????????????????? -??? ???????????? -??? ??????????????????
          return service.queryAllYaerAndMonthMaxInoid()
              .flatMap(maxList -> {
                map.put("maxInoId", maxList);
                // ??????????????????????????????
                if (new_ListMap.size() > 0) { // ???????????????????????? ????????????????????????????????????
                  return service.checkPingZhengYearIsClose(new_ListMap).flatMap(item -> {
                    if (item.length > 0) {
                      map.put("error", "??????????????????" + item[0] + "??? ?????????????????????,???????????????????????????");
                      map.put("code", "404");
                    } else if (item.length == 0 && parameter[4].equals("1")) {
                      return service.checkPingZhengDbRepeatNumber(new_ListMap).flatMap(item1 -> {
                        if (item1.length > 0) {
                          map.put("error", "?????????????????????????????????" + JSON.toJSONString(item1) + "???????????????????????????????????????????????????????????????");
                          map.put("code", "404");
                        }
                        return Mono.just(map);
                      });
                    }
                    return Mono.just(map);
                  });
                }
                return Mono.just(map);
              });
        }).flatMap(
            map1 -> service.getAllKuaiJiQiJianInfoByAccId(thisDbNameAR.get()).doOnNext(map3 -> map1.put("kuaiJiQiJians", map3)).thenReturn(map1)
        )
        // ??????map??????,?????????????????????
        .flatMapMany(map -> {
          String error = (String) map.get("error");
          String code = (String) map.get("code");
          if (StringUtils.isNotBlank(error)) {
            return Mono.just(R.ok().setResult(error).setCode(Long.valueOf(code)));
          }
          List<Map<Object, Object>> timelist = (List<Map<Object, Object>>) map.get("timelist");
          List<Object[]> excellist = (List<Object[]>) map.get("excellist");
          List<Accvoucher> coutnoIdList = (List<Accvoucher>) map.get("maxInoId");
          Map<String, Object> kjQjMap = (HashMap<String, Object>) map.get("kuaiJiQiJians");
          //List<SysPeriod> priedList = (List<SysPeriod>)kjQjMap.get("list");// ??????db??????????????????
          HashSet<CodeKemu> kemuList = (HashSet<CodeKemu>) map.get("kemuList");
          List<Accvoucher> saveList = new ArrayList();
          String[] systemTitleNames = titlesAR.get();
          int dateIndex = -1;
          int numberIndex = -1;
          int idocIndex = -1; //?????????
          int cdigestIndex = -1; //????????????
          int ccodeIndex = -1; //????????????
          int ccodeNameIndex = -1; //????????????
          int mdIndex = -1; //????????????
          int mcIndex = -1; // ????????????
          int cashProjectIndex = -1; //????????????????????????
          int cdeptIdIndex = -1; //????????????
          int cpersonIdIndex = -1; //????????????
          int ccusIdIndex = -1; //????????????
          int csupIdIndex = -1; //???????????????
          int projectClassIdIndex = -1; //??????????????????
          int projectIdIndex = -1; //????????????
          int cbillIndex = -1; //?????????
          int ccheckIndex = -1; // ?????????
          int cdirectorIndex = -1; // ?????????
          int ccashierIndex = -1; //?????????
          int iflagIndex = -1;//  ????????????
          int cbookIndex = -1; //??????
          int csignIndex = -1; //????????????
          int settlementMethodIndex = -1; //??????????????????
          int unitMeasurementIndex = -1; //????????????
          int ndSIndex = -1; //????????????
          int ncSIndex = -1; //????????????
          int unitPriceIndex = -1; //??????
          int foreignCurrencyIndex = -1; //????????????
          int nfratMdIndex = -1; //'??????????????????'
          int nfratMcIndex = -1; //'??????????????????'
          int mdFIndex = -1; //????????????
          int mcFIndex = -1; //????????????
          int pjIdIndex = -1; //?????????
          int pjDateIndex = -1; //????????????
          int pjUnitNameIndex = -1; //????????????
          int cdfine1Index = -1; //?????????1
          String thisImportDate = DateUtil.today();
          int sameDateNumber = 0;
          String sameDateStr = "";
          for (int i = 0; i < timelist.size(); i++) {
            // ?????????
            String uniqueCode = IdUtil.objectId();
            int inidNumber = 0;
            String thisCode = "";
            String coutnoId = "";
            if (!parameter[4].equals("1")) { //?????????
              // ????????????????????????
              String importDate = timelist.get(i).get("time").toString().split(">>")[0].substring(0, 7);
              if (!sameDateStr.equals(importDate)) {
                sameDateNumber = 0;
                sameDateStr = importDate;
              } else {
                sameDateNumber++;
              }
              coutnoId = getThisDateMaxVoucherIdon(coutnoIdList, timelist.get(i).get("time").toString());
              if ((Integer.valueOf(coutnoId) + sameDateNumber + 1) < 10) {
                coutnoId = "000" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
              } else if ((Integer.valueOf(coutnoId) + sameDateNumber + 1) >= 10 && (Integer.valueOf(coutnoId) + sameDateNumber + 1) < 100) {
                coutnoId = "00" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
              } else if ((Integer.valueOf(coutnoId) + sameDateNumber + 1) >= 100 && (Integer.valueOf(coutnoId) + sameDateNumber + 1) < 1000) {
                coutnoId = "0" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
              }
            }
            // ??????????????????
            for (int j = 0; j < excellist.size(); j++) {
              Object[] row = excellist.get(j);
              // ????????????????????????????????????
              if (j == 0 && i == 0) { // ???????????????index
                for (int k = 0; k < row.length; k++) {
                  if (systemTitleNames[k].equals("????????????")) dateIndex = k;
                  if (systemTitleNames[k].equals("?????????")) numberIndex = k;
                  if (systemTitleNames[k].equals("????????????")) idocIndex = k;
                  if (systemTitleNames[k].equals("????????????")) cdigestIndex = k;
                  if (systemTitleNames[k].equals("????????????")) ccodeIndex = k;
                  if (systemTitleNames[k].equals("????????????")) ccodeNameIndex = k;
                  if (systemTitleNames[k].equals("????????????")) mdIndex = k;
                  if (systemTitleNames[k].equals("????????????")) mcIndex = k;
                  if (systemTitleNames[k].equals("????????????????????????")) cashProjectIndex = k;

                  if (systemTitleNames[k].equals("????????????")) cdeptIdIndex = k;
                  if (systemTitleNames[k].equals("????????????")) cpersonIdIndex = k;
                  if (systemTitleNames[k].equals("????????????")) ccusIdIndex = k;
                  if (systemTitleNames[k].equals("???????????????")) csupIdIndex = k;
                  if (systemTitleNames[k].equals("??????????????????")) projectClassIdIndex = k;
                  if (systemTitleNames[k].equals("????????????")) projectIdIndex = k;

                  if (systemTitleNames[k].equals("?????????")) cbillIndex = k;
                  if (systemTitleNames[k].equals("?????????")) ccheckIndex = k;
                  if (systemTitleNames[k].equals("?????????")) cbookIndex = k;
                  if (systemTitleNames[k].equals("????????????")) iflagIndex = k;
                  if (systemTitleNames[k].equals("???????????????")) cdirectorIndex = k;
                  if (systemTitleNames[k].equals("????????????")) csignIndex = k;
                  if (systemTitleNames[k].equals("???????????????")) ccashierIndex = k;

                  if (systemTitleNames[k].equals("??????????????????")) settlementMethodIndex = k;
                  if (systemTitleNames[k].equals("????????????")) unitMeasurementIndex = k;
                  if (systemTitleNames[k].equals("????????????")) ndSIndex = k;
                  if (systemTitleNames[k].equals("????????????")) ncSIndex = k;
                  if (systemTitleNames[k].equals("????????????")) foreignCurrencyIndex = k;
                  if (systemTitleNames[k].equals("??????????????????")) nfratMdIndex = k;
                  if (systemTitleNames[k].equals("??????????????????")) nfratMcIndex = k;
                  if (systemTitleNames[k].equals("????????????")) mdFIndex = k;
                  if (systemTitleNames[k].equals("????????????")) mcFIndex = k;
                  if (systemTitleNames[k].equals("??????")) unitPriceIndex = k;
                  if (systemTitleNames[k].equals("?????????")) pjIdIndex = k;
                  if (systemTitleNames[k].equals("????????????")) pjDateIndex = k;
                  if (systemTitleNames[k].equals("??????????????????")) pjUnitNameIndex = k;
                  if (systemTitleNames[k].equals("?????????1")) cdfine1Index = k;
                }
              }
              // ??????????????????  ?????????????????????  ?????? ??????????????????  ????????????????????????
              String importDate = timelist.get(i).get("time").toString().split(">>")[0];
              if (importDate.equals(row[dateIndex]) &&
                  (*//*!parms[4].equals("1") || *//*timelist.get(i).get("time").toString().split(">>")[1].equals(row[numberIndex]))
              ) {
                String pingZhengNumber = (parameter[4].equals("1")) ? excellist.get(j)[numberIndex].toString().trim() : coutnoId.trim();
                //if (!parms[4].equals("1") && !pingZhengNumber.equals("")) break;
                String time = timelist.get(i).get("time").toString().replaceAll("-", "");
                // ??????????????????????????????
                if (!thisCode.equals(pingZhengNumber)) {
                  inidNumber = 0;
                  thisCode = pingZhengNumber;
                } else {
                  inidNumber += 1;
                }
                String tYear = time.substring(0, 4);
                String tMonth = time.substring(4, 6);
                String tQj = ((HashMap<String, String>) kjQjMap.get(tYear)).get(tMonth);//???????????????????????? ?????????
                Accvoucher accvoucher = new Accvoucher() //??????
                    .setUniqueCode(uniqueCode)
                    .setVouchUnCode(IdUtil.objectId())
                    .setIfrag(setVoucherStauts(setColValue(row, iflagIndex)))
                    .setCsign(setColValue(row, csignIndex))
                    .setIyear(StrUtil.trim(tYear))
                    .setImonth(StrUtil.trim(tMonth))
                    .setIyperiod(tYear + tQj) // ????????????
                    .setIperiod(tQj) //??????
                    .setCbill(setColValue(row, cbillIndex))
                    .setDbillDate(StrUtil.trim(importDate))
                    .setInoId(StrUtil.trim(pingZhengNumber))
                    .setInid(String.valueOf(inidNumber) + 1)
                    .setIdoc(setVoucherIdoc(setColValue(row, idocIndex)))
                    .setCdigest(setColValue(row, cdigestIndex))
                    .setCcode(setColValue(row, ccodeIndex))
                    .setCcodeName(setColValue(row, ccodeNameIndex))
                    .setMd(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, mdIndex)), 2))
                    .setMc(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, mcIndex)), 2))
                    .setCsign(setColValue(row, csignIndex))
                    .setCdirector(setColValue(row, cdirectorIndex))
                    .setCashProject(setColValue(row, cashProjectIndex))
                    .setPjCsettle(setColValue(row, settlementMethodIndex))
                    .setPjId(setColValue(row, pjIdIndex))
                    .setPjDate(setColValue(row, pjDateIndex))
                    .setPjUnitName(setColValue(row, pjUnitNameIndex));
                // ????????????
                if (StringUtils.isNotBlank(setColValue(row, ccheckIndex)))
                  accvoucher.setCcheck(setColValue(row, ccheckIndex)).setCcheckDate(thisImportDate);
                if (StringUtils.isNotBlank(setColValue(row, ccashierIndex)))
                  accvoucher.setCcashier(setColValue(row, ccashierIndex)).setCcashierDate(thisImportDate);
                if (StringUtils.isNotBlank(setColValue(row, cbookIndex)))
                  accvoucher.setCbook(setColValue(row, cbookIndex)).setIbook("1").setIbookDate(thisImportDate);
                CodeKemu thisKemu = getThisKemu(setColValue(row, ccodeIndex), kemuList);
                if (thisKemu.getBdept().equals("1"))
                  accvoucher.setCdeptId(setColValue(row, cdeptIdIndex));
                if (thisKemu.getBperson().equals("1"))
                  accvoucher.setCpersonId(setColValue(row, cpersonIdIndex));
                if (thisKemu.getBcus().equals("1"))
                  accvoucher.setCcusId(setColValue(row, ccusIdIndex));
                if (thisKemu.getBsup().equals("1"))
                  accvoucher.setCsupId(setColValue(row, csupIdIndex));
                if (thisKemu.getBitem().equals("1"))
                  accvoucher.setProjectClassId(setColValue(row, projectClassIdIndex))
                      .setProjectId(setColValue(row, projectIdIndex));
                if (parameter[1].equals("1")) {               //??????
                  accvoucher
                      .setNdS(setColValue(row, ndSIndex).replaceAll(",", ""))
                      .setNcS(setColValue(row, ncSIndex).replaceAll(",", ""))
                      .setNfratMd(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, nfratMdIndex).replaceAll(",", "")), 2))
                      .setNfratMc(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, nfratMcIndex).replaceAll(",", "")), 2))
                      .setCunitPrice(setColValue(row, unitPriceIndex))
                      .setMdF(setColValue(row, mdFIndex))
                  //.setMcF(setColValue(row, mcFIndex))
                  ;
                  if (false) {// ?????????????????????????????? ?????? ??? ???????????? ??????
                    String ccode = accvoucher.getCcode();
                    accvoucher.setUnitMeasurement(setColValue(row, unitMeasurementIndex))
                        .setForeignCurrency(setColValue(row, foreignCurrencyIndex));
                  }
                }
                // ??????????????????????????? ?????????????????????????????????
                // if (systemTitleNames[k].equals("?????????1"))cdfine1Index = k;
                if (systemTitleNames.toString().contains("?????????")) { // ???????????????
                  List<Integer> keys = new ArrayList<>();
                  List<Integer> indexs = new ArrayList<>();
                  for (int k = 0; k < systemTitleNames.length; k++) {
                    String titleName = systemTitleNames[k];
                    String numStr = titleName.replace("?????????", "");
                    if (titleName.startsWith("?????????") && NumberUtil.isNumber(numStr)) {
                      keys.add(Integer.parseInt(numStr));
                      indexs.add(k);
                    }
                  }
                  if (keys.size() > 30) {
                    return Mono.just(R.ok().setResult("?????????????????????????????????????????????????????????30????????????").setCode(404L));
                  } else if (keys.size() > 0) {
                    accvoucher = service.modifyPingZhengEntityPropertyByAuxiliaryItem(accvoucher, excellist.get(j), keys, indexs);
                  }
                }
                saveList.add(accvoucher);
              }
            }
          }
          return Flux.just(new Accvoucher());
          //return accvoucherRepository.saveAll(saveList);

        });
        /*.collectList().flatMap(list ->
            taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(list)
        )
        .map(o -> R.ok().setResult(o));*/
    }


    @PostMapping("/downCheckFile")
    public Mono<Void> downCheckFile(@RequestBody Map map, ServerHttpResponse response) throws Exception {
        String fileName = map.get("parm").toString();
        return Mono.fromCallable(() -> {
            File file = new File(fileName);
            return file;
        }).flatMap(file -> downloadFile(response, file, "???????????????" + fileName.split("-")[fileName.split("-").length - 1]));
    }


    @PostMapping("/delCheckFile")
    public Mono<R> delCheckFile(@RequestBody Map map) throws Exception {
        return Mono.just(map.get("parm").toString()).map(fileName -> {
            File file = new File(fileName);
            try {
                file.delete();
                return R.ok();
            } catch (Exception e) {
                return R.error();
            }
        });
    }

    private Mono<Void> downloadFile(ServerHttpResponse response, File file, String fileName) {
        ZeroCopyHttpOutputMessage zeroCopyHttpOutputMessage = (ZeroCopyHttpOutputMessage) response;
        try {
            response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=".concat(URLEncoder.encode(fileName, StandardCharsets.UTF_8.displayName())));
//      response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return zeroCopyHttpOutputMessage.writeWith(file, 0, file.length());
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException();
        } finally {
            // ????????????
            file.delete();
        }
    }


    /**
     * ?????????????????????Excel
     */
    private String WriteCheckInfoToExcel(String tempPath, List<Object[]> list, int index) {
        return new XlsUtils3(tempPath).changeExcelByList(tempPath, list, index);
    }

    @Autowired
    R2dbcRouterLoader r2dbcRouterLoader;

    @Transactional
    @PostMapping("/importAccvoucher") // ??????
    public Mono<R> listOCR2(@RequestPart("file") FilePart filePartParm, @RequestPart("templateInfo") String templateID) throws Exception {
        // ????????? -- ???????????? -- ??????????????? -- ???????????? -- ?????????????????????--??????accId--???????????????????????????????????????
        String[] parms = templateID.split("--");
        AtomicReference<String[]> titlesAR = new AtomicReference(); // ????????????????????????
        AtomicReference<String> thisDbName = new AtomicReference(parms[5]); // ??????????????????
        Path tempFilePath = Files.createTempFile("", new String(filePartParm.filename().getBytes("ISO-8859-1"), "UTF-8"));
        return Mono.just(filePartParm).flatMap(files -> taskRepository.save(new Task().setCaozuoUnique("test001").setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(DateUtil.thisMonth() + "").setMethod("??????")).map(entity1 -> files)).flatMap(filePart -> {
                    try {
                        return DataBufferUtils.write(filePart.content(), AsynchronousFileChannel.open(tempFilePath, StandardOpenOption.WRITE), 0).doOnComplete(() -> log.info("????????????")).collectList().map(item -> tempFilePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return Mono.just("");
                })// ?????????????????????
                // ??????????????????????????????
                .flatMap(item -> service.getTheHeaderOfTheCurrentlyImportedFile(parms[0], parms[1], parms[2])).flatMap(item -> Mono.just(item.getT2()).doOnNext(tits -> {
                                    if (null == titlesAR.get()) titlesAR.set(new String[tits.size() - 1]);
                                    titlesAR.set(tits.toArray(new String[tits.size() - 1]));
                                })    // ????????????
                                .map(tits -> item.getT1().toArray(new String[tits.size() - 1]))              // ????????????
                )
                // ????????????excel??????
                .flatMap(titles -> { //??????????????????????????????
                    List<Object[]> list = null;
                    try {
                        list = new XlsUtils3(tempFilePath.toString()).getExcelObj(tempFilePath.toString(), titles, 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        assert tempFilePath != null;
                        try {
                            Files.delete(tempFilePath);
                        } catch (IOException e) {
                            System.err.println("???????????????????????????????????????: ---------???---------");
                            e.printStackTrace();
                        }
                    }
                    return Mono.just(list);
                })
                // ??????????????????
                .flatMap(list -> {
                    Map mapArr = new HashMap();
                    mapArr.put("excellist", list);   // excel?????????
                    mapArr.put("error", "");
                    mapArr.put("code", "200");
                    if (null == list || list.size() < 2) {  // ??????????????????
                        mapArr.put("error", "??????????????????????????????????????????????????????????????????????????????????????????");
                        mapArr.put("code", "404");
                        return Mono.just(mapArr);
                    }
                    // ????????????????????????
                    String[] systemTitleNames = titlesAR.get();
                    Set<String> projects = new HashSet<>();
                    int voucherNumberIndex = -1;
                    int dateIndex = -1;
                    int mdIndex = -1;
                    int mcIndex = -1;
                    String thisImportYearStr = "";
                    int pzNumber = 0;
                    String pzStr = "";
                    // ????????????
                    double mdSum = 0;
                    double mcSum = 0;
                    int ilen = list.size() - 1;
                    for (int i = 0; i < list.size(); i++) {
                        Object[] rows = list.get(i);
                        if (i == 0) { // ?????????????????????
                            for (int j = 0; j < rows.length; j++) {
                                if (systemTitleNames[j].equals("?????????")) voucherNumberIndex = j;
                                if (systemTitleNames[j].equals("????????????")) dateIndex = j;
                                if (systemTitleNames[j].equals("????????????")) mdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) mcIndex = j;
                            }
                        }
                        for (int j = 0; j < rows.length; j++) {
                            if (systemTitleNames[j].equals("????????????") && rows[j] == "") {
                                mapArr.put("error", "????????????" + rows[dateIndex] + " " + rows[voucherNumberIndex] + "???:????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (systemTitleNames[j].equals("????????????") && i == 0) {
                                thisImportYearStr = rows[dateIndex].toString().substring(0, 4);
                            }

                            // ???????????? ?????????
                            if (systemTitleNames[j].equals("?????????") && (null == rows[voucherNumberIndex] || rows[voucherNumberIndex] == "") /*&& parms[4].equals("1")*/) {
                                mapArr.put("error", "???????????????" + rows[dateIndex] + "???:?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (systemTitleNames[j].equals("?????????")) {
                                String pzValue = setColValue(rows, voucherNumberIndex);
                                BigDecimal mdB = new BigDecimal("0");
                                BigDecimal mcB = new BigDecimal("0");
                                if (rows[mdIndex] != "") {
                                    mdB = mdB.add(new BigDecimal(rows[mdIndex].toString()));
                                }
                                if (rows[mcIndex] != "") {
                                    mcB = mcB.add(new BigDecimal(rows[mcIndex].toString()));
                                }
                                // ???????????????
                                if (mdB.doubleValue() == 0 && mcB.doubleValue() == 0) {
                                    mapArr.put("error", "???????????????" + rows[dateIndex] + "???:????????????" + pzValue + "???:???????????????????????????0");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                                if (StrUtil.equals(pzStr, pzValue)) {
                                    pzNumber++;
                                    mdSum += mdB.doubleValue();
                                    mcSum += mcB.doubleValue();
                                } else {
                                    if (StrUtil.isNotBlank(pzStr) && pzNumber < 2) {
                                        mapArr.put("error", "????????????" + pzStr + "????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    }
                                    if (!NumberUtil.equals(new BigDecimal(mdSum).setScale(2, BigDecimal.ROUND_HALF_UP), new BigDecimal(mcSum).setScale(2, BigDecimal.ROUND_HALF_UP))) {
                                        mapArr.put("error", "????????????" + pzStr + "????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    }
                                    pzNumber = 1;
                                    pzStr = pzValue;
                                    mdSum = mdB.doubleValue();
                                    mcSum = mcB.doubleValue();
                                    if (i == ilen && pzNumber == 1) {//?????????????????????????????????
                                        mapArr.put("error", "????????????" + pzStr + "????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    }
                                }
                            }
                            // ????????????
                            if (systemTitleNames[j].equals("????????????") && rows[j] == "") {
                                mapArr.put("error", "????????????" + rows[dateIndex] + " " + rows[voucherNumberIndex] + "???:????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            if (systemTitleNames[j].equals("????????????") && rows[j] == "") {
                                mapArr.put("error", "????????????" + rows[dateIndex] + " " + rows[voucherNumberIndex] + "???:????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            if (systemTitleNames[j].equals("?????????") && rows[j] == "") {
                                mapArr.put("error", "????????????" + rows[dateIndex] + " " + rows[voucherNumberIndex] + "???:?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            if (systemTitleNames[j].equals("????????????")) {//????????????
                                if (rows[j] == "" || (!rows[j].equals("???") || !rows[j].equals("???"))) {
                                    rows[j] = "???";
                                }
                            }
                            // ???????????????????????????????????????????????????????????????????????????????????????????????????

                            if (systemTitleNames[j].equals("??????????????????") && null != rows[j] && NumberUtil.isNumber(rows[j].toString()) && (Integer.parseInt(rows[j].toString()) < 11)) {
                                projects.add(rows[j].toString());
                            }
                        }
                    }
                    String finalThisImportYearStr = thisImportYearStr;
                    return Mono.just("").map(s -> {
                        //?????????????????????????????????
                        Mono<HashSet<String[]>> all = codeKemuRepository.findAllByYear(finalThisImportYearStr).collectList().map(list1 -> new HashSet<>(getHashSetByKeMu(list1)));
                        Mono<HashSet<CodeKemu>> lastStage = codeKemuRepository.findAllByYearAndBend(finalThisImportYearStr).collectList().map(list1 -> new HashSet<>(list1));
                        // ??????????????????
                        Mono<List<SysPsn>> geSets = psnRepository.findAllPsnCodeOrPsnNameByFlag().collectList();
                        Mono<List<SysDepartment>> bmSets = departmentRepository.findAllDeptCodeOrDeptNameByFlag().collectList();
                        Mono<List<Customer>> khSets = customerRepository.findAllCustCodeOrCustNameByFlag().collectList();
                        Mono<List<Supplier>> gysSets = supplierRepository.findAllCustCodeOrCustNameByFlag().collectList();
                        Mono<List<Project>> proSets = projectRepository.findAllProjectCodeOrProjectNameByAll().collectList(); // ????????????
                        Mono<List<ProjectItem>> proItemSets = projectItemRepository.findAllItemCodeOrItemNameByAll().collectList(); // ????????????

                                /*
                                Mono<Map<String, Set<String>>> proMap = null;
                                if (projects.size() > 0) {
                                    proMap = Mono.just(new HashMap<String, Set<String>>()).map(maps ->
                                            Flux.fromIterable(projects)
                                                    .flatMap(proNum -> projectService.findByProjectCodeAndValue(proNum, parms[2])
                                                            .doOnNext(sets -> maps.put(proNum, sets))
                                                    ).collectList().map(list1 -> maps)
                                    ).flatMap(a -> a);
                                } else {
                                    proMap = Mono.just(new HashMap<>());
                                }*/
                        // ????????????????????????
                        Mono<Map<String, Object>> xjMap = accountInfoRepository.findAll().collectList().flatMap(acclist -> {
                            if (acclist.size() > 0 && null != acclist.get(0)) {
                                //thisDbName.set(acclist.get(0).getAccCode());
                                // ????????????????????????
                                //return service.queryAccountByAccId(acclist.get(0).getAccCode()).map(entity -> (null != entity.getIcashFlow() && entity.getIcashFlow().equals("1")) ? true : false);
                                return Mono.just(false);
                            }
                            return Mono.just(false);
                        }).flatMap(isTrue -> {
                            HashMap<String, Object> maps = new HashMap<>();
                            maps.put("XJCheck", isTrue);
                            maps.put("XJList", new HashSet<String>());
                            if (isTrue) {
                                return projectCashRepository.findByProjectAllOrderByCode().collectList().map(list1 -> {
                                    maps.put("XJList", new HashSet<>(list1));
                                    return maps;
                                });
                            }
                            return Mono.just(maps);
                        });
                        return Tuples.of(Mono.zip(all, lastStage, geSets, bmSets, khSets, gysSets, xjMap), Mono.zip(proItemSets, proSets));
                    }).flatMap(zips -> zips.getT1().flatMap(many -> {
                        mapArr.put("codeSets", many);
                        return zips.getT2().flatMap(many2 -> {
                            mapArr.put("proSets", many2);
                            return Mono.just(mapArr);
                        });
                    }));
                })
                // ????????????????????????????????????????????????????????????????????????
                .flatMap(map -> {
                    String error = (String) map.get("error");
                    if (StringUtils.isNotBlank(error)) {
                        Map<String, String> mapArr = new HashMap<>();
                        mapArr.put("error", error);
                        mapArr.put("code", "404");
                        return Mono.just(mapArr);
                    }
                    List<Object[]> list = (List<Object[]>) map.get("excellist");
                    List<Map<String, Object>> ListMap = new ArrayList<>();
                    // ????????????????????????
                    String[] systemTitleNames = titlesAR.get();
                    int dateIndex = 0;
                    int numberIndex = 0;
                    int subjectNumIndex = 0;
                    int subjectNameIndex = 0;
                    int ndSIndex = 0; //????????????
                    int ncSIndex = 0; //????????????
                    int unitPriceIndex = 0; //??????
                    int mdFIndex = 0; //????????????
                    int mcFIndex = 0; //????????????
                    int cdeptIdIndex = 0; //????????????
                    int cpersonIdIndex = 0; //????????????
                    int ccusIdIndex = 0; //????????????
                    int csupIdIndex = 0; //???????????????
                    int projectClassIdIndex = 0; //??????????????????
                    int projectIdIndex = 0; //????????????
                    int cashProjectIndex = 0; //????????????????????????
                    int nfratMdIndex = -1; //'??????????????????'
                    int nfratMcIndex = -1; //'??????????????????'
                    // ???????????????????????? ?????? ??? ?????? boo
                    Boolean fx = parms[2].equals("1");
                    // ???????????? ??? ????????????
                    Tuple7<HashSet<String[]>, HashSet<CodeKemu>, List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, Map<String, Object>> beingSets = (Tuple7<HashSet<String[]>, HashSet<CodeKemu>, List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, Map<String, Object>>) map.get("codeSets");
                    Tuple2<List<ProjectItem>, List<Project>> prosSets = (Tuple2<List<ProjectItem>, List<Project>>) map.get("proSets");
                    for (int i = 0; i < list.size(); i++) {
                        Object[] row = list.get(i);
                        // ????????????????????????????????????
                        if (i == 0) {
                            for (int j = 0; j < row.length; j++) {
                                if (systemTitleNames[j].equals("????????????")) dateIndex = j;
                                if (systemTitleNames[j].equals("?????????")) numberIndex = j;
                                if (systemTitleNames[j].equals("????????????")) subjectNumIndex = j;
                                if (systemTitleNames[j].equals("????????????")) subjectNameIndex = j;
                                if (systemTitleNames[j].equals("????????????")) ndSIndex = j;
                                if (systemTitleNames[j].equals("????????????")) ncSIndex = j;
                                if (systemTitleNames[j].equals("??????")) unitPriceIndex = j;
                                if (systemTitleNames[j].equals("??????????????????")) nfratMdIndex = j;
                                if (systemTitleNames[j].equals("??????????????????")) nfratMcIndex = j;

                                if (systemTitleNames[j].equals("????????????")) mdFIndex = j;
//                        if (systemTitleNames[j].equals("????????????")) mcFIndex = j;

                                if (systemTitleNames[j].equals("????????????")) cdeptIdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) cpersonIdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) ccusIdIndex = j;
                                if (systemTitleNames[j].equals("???????????????")) csupIdIndex = j;
                                if (systemTitleNames[j].equals("??????????????????")) projectClassIdIndex = j;
                                if (systemTitleNames[j].equals("????????????")) projectIdIndex = j;

                                if (systemTitleNames[j].equals("????????????????????????")) cashProjectIndex = j;
                            }
                        }
                        String codeErrorStr = "??????????????????" + row[dateIndex] + " " + row[numberIndex] + "??????????????????????????????????????????:";
                        Map<String, Object> mm = new HashMap<>();
                        String tDate = DateUtil.formatDate(DateUtil.parseDate(row[dateIndex].toString()));
                        mm.put("time", tDate + ">>" + row[numberIndex]);
                        ListMap.add(mm);
                        // ???????????????????????????????????????row[subjectNumIndex].toString()
                        Object codeValue = row[subjectNumIndex];
                        Map<String, String> mapArr = new HashMap<>();
                        if (null == codeValue || org.apache.commons.lang3.StringUtils.isBlank(codeValue.toString())) {
                            mapArr.put("error", "??????????????????" + row[dateIndex] + "???:??????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        } else if (!checkImportKemuExist(beingSets.getT1(), codeValue, "2")) {
                            mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????????????????????????????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        } else if (checkImportKemuExist(beingSets.getT1(), codeValue, "1")) {
                            mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????????????????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        } else {
                            HashSet<CodeKemu> kemuList = beingSets.getT2();
                            CodeKemu thisKemu = getThisKemu(codeValue, kemuList);
                            if (null == thisKemu) {
                                mapArr.put("error", codeErrorStr + codeValue + "???????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else {
                                // ??????????????????
                                if ((boolean) beingSets.getT7().get("XJCheck") && (StringUtils.isNotBlank(thisKemu.getBcash()) || StringUtils.isNotBlank(thisKemu.getBbank()))) {
                                    String cash = setColValue(row, cashProjectIndex);
                                    Set<String> xjSets = (HashSet<String>) beingSets.getT7().get("XJList");
                                    if (StringUtils.isBlank(cash)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "???????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (StringUtils.isNotBlank(cash) && !xjSets.contains(cash)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    }
                                }
                                // ??????????????????
                                if (thisKemu.getBdept().equals("1")) {
                                    String dValue = (null == row[cdeptIdIndex] ? "" : row[cdeptIdIndex].toString());
                                    List<SysDepartment> deptCollect = beingSets.getT4().stream().filter(item -> (fx ? item.getDeptName().equals(dValue) : item.getDeptCode().equals(dValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(dValue)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (deptCollect.size() == 0) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + dValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else {
                                        row[cdeptIdIndex] = deptCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBperson().equals("1")) {
                                    String grValue = (null == row[cpersonIdIndex] ? "" : row[cpersonIdIndex].toString());
                                    List<SysPsn> psnCollect = beingSets.getT3().stream().filter(item -> (fx ? item.getPsnName().equals(grValue) : item.getPsnCode().equals(grValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(grValue)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (psnCollect.size() == 0) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + grValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else {
                                        row[cpersonIdIndex] = psnCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBcus().equals("1")) {
                                    String cValue = (null == row[ccusIdIndex] ? "" : row[ccusIdIndex].toString());
                                    List<Customer> customerCollect = beingSets.getT5().stream().filter(item -> (fx ? (item.getCustName().equals(cValue) || item.getCustAbbname().equals(cValue)) : item.getCustCode().equals(cValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(cValue)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (customerCollect.size() == 0) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + cValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else {
                                        row[ccusIdIndex] = customerCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBsup().equals("1")) {
                                    String gyValue = (null == row[csupIdIndex] ? "" : row[csupIdIndex].toString());
                                    List<Supplier> supCollect = beingSets.getT6().stream().filter(item -> (fx ? (item.getCustName().equals(gyValue) || item.getCustAbbname().equals(gyValue)) : item.getCustCode().equals(gyValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(gyValue)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (supCollect.size() == 0) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + gyValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else {
                                        row[csupIdIndex] = supCollect.get(0).getUniqueCode();
                                    }
                                }

                                if (thisKemu.getBitem().equals("1")) {
                                    String pValue = (null == row[projectIdIndex] ? "" : row[projectIdIndex].toString());
                                    if (parms[6].equals("1")) { // ??????????????????
                                        String projectClassId = thisKemu.getProjectClassId();
                                        if (StrUtil.isBlank(projectClassId)) {
                                            mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????????????????????????????ID???????????????,???????????????????????????");
                                            mapArr.put("code", "404");
                                            return Mono.just(mapArr);
                                        } else if (StringUtils.isNotBlank(projectClassId) && prosSets.getT1().stream().filter(it -> it.getId().equals(projectClassId)).collect(Collectors.toList()).size() == 0) {
                                            mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "" + pValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????" + projectClassId + "???");
                                            mapArr.put("code", "404");
                                            return Mono.just(mapArr);
                                        }
                                    }
                                    List<Project> proCollect = prosSets.getT2().stream().filter(item -> (fx ? (item.getProjectName().equals(pValue)) : item.getProjectCode().equals(pValue))).collect(Collectors.toList());
                                    if (StringUtils.isBlank(pValue)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (proCollect.size() == 0) {
                                        mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + pValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else {
                                        row[projectIdIndex] = proCollect.get(0).getUniqueCode();
                                    }
                                }
                                row[subjectNameIndex] = thisKemu.getCcodeName(); // ??????????????????????????????????????????
                                if (!parms[1].equals("1")) { // ?????????????????????
                                    continue;
                                }
                                if (thisKemu.getBnum().equals("1")) {//?????????????????? ?????? ??? ????????????
                                    String price = setColValue(row, unitPriceIndex);
                                    String jNum = setColValue(row, ndSIndex);
                                    String dNum = setColValue(row, ncSIndex);
                                    if (StringUtils.isBlank(price) || (StringUtils.isBlank(jNum) && StringUtils.isBlank(dNum)) || (StringUtils.isNotBlank(jNum) && StringUtils.isNotBlank(dNum))) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????,??????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if (StringUtils.isNotBlank(price) && (!NumberUtil.isNumber(price) || Double.parseDouble(price) < 0)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "???????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if ((StringUtils.isNotBlank(jNum) && !NumberUtil.isNumber(jNum)) || (StringUtils.isNotBlank(dNum) && !NumberUtil.isNumber(dNum))) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    }
                                }
                                if (thisKemu.getCurrency().equals("1")) {//???????????????????????? ?????? ??? ?????? ????????????
                                    String amountMd = setColValue(row, nfratMdIndex).replaceAll(",", "");
                                    String amountMc = setColValue(row, nfratMcIndex).replaceAll(",", "");
                                    String jNum = setColValue(row, mdFIndex);
                                    //String dNum = row[mcFIndex].toString();
                                    if ((StringUtils.isBlank(amountMd) && StringUtils.isBlank(amountMc)) || (StringUtils.isBlank(jNum) /*&& StringUtils.isBlank(dNum)) || (StringUtils.isNotBlank(jNum) && StringUtils.isNotBlank(dNum)*/)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if ((StringUtils.isNotBlank(amountMd) && (!NumberUtil.isNumber(amountMd))) || (StringUtils.isNotBlank(amountMc) && (!NumberUtil.isNumber(amountMc))/* || Double.parseDouble(amount) < 0*/)) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    } else if ((StringUtils.isNotBlank(jNum) && (!NumberUtil.isNumber(jNum)/* || Double.parseDouble(jNum) < 0*/)) /*|| (StringUtils.isNotBlank(dNum) && (!NumberUtil.isNumber(dNum) || Double.parseDouble(dNum) < 0))*/) {
                                        mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????????????????");
                                        mapArr.put("code", "404");
                                        return Mono.just(mapArr);
                                    }
                                }
                            }
                        }

                    }
                    //??????????????????list
                    List<Map<Object, Object>> new_ListMap = new ArrayList<Map<Object, Object>>();
                    Set arr = new HashSet();
                    for (int i = 0; i < ListMap.size(); i++) {
                        Map m = ListMap.get(i);
                        if (arr.contains(m.get("time"))) {
                            continue;
                        }
                        //??????key?????????set
                        arr.add(m.get("time"));
                        //??????????????????
                        new_ListMap.add(m);
                    }
                    map.put("timelist", new_ListMap);
                    map.put("kemuList", beingSets.getT2());
                    // ???????????????????????? -??? ???????????? -??? ??????????????????
                    return service.queryAllYaerAndMonthMaxInoid().flatMap(maxList -> {
                        map.put("maxInoId", maxList);
                        // ??????????????????????????????
                        if (new_ListMap.size() > 0) { // ???????????????????????? ????????????????????????????????????
                            return service.checkPingZhengYearIsClose(new_ListMap).flatMap(item -> {
                                if (item.length > 0) {
                                    map.put("error", "??????????????????" + item[0] + "??? ?????????????????????,???????????????????????????");
                                    map.put("code", "404");
                                } else if (item.length == 0 && parms[4].equals("1")) {
                                    return service.checkPingZhengDbRepeatNumber(new_ListMap).flatMap(item1 -> {
                                        if (item1.length > 0) {
                                            map.put("error", "?????????????????????????????????" + JSON.toJSONString(item1) + "???????????????????????????????????????????????????????????????");
                                            map.put("code", "404");
                                        }
                                        return Mono.just(map);
                                    });
                                }
                                return Mono.just(map);
                            });
                        }
                        return Mono.just(map);
                    });
                }).flatMap(map1 -> StrUtil.isNotBlank(map1.get("error").toString()) ? Mono.just(map1) : service.getAllKuaiJiQiJianInfoByAccId(thisDbName.get()).doOnNext(map3 -> map1.put("kuaiJiQiJians", map3)).thenReturn(map1))
                // ??????map??????,?????????????????????
                .flatMapMany(map -> {
                    String error = (String) map.get("error");
                    String code = (String) map.get("code");
                    if (StringUtils.isNotBlank(error)) {
                        return Mono.just(R.ok().setResult(error).setCode(Long.valueOf(code)));
                    }
                    List<Map<Object, Object>> timelist = (List<Map<Object, Object>>) map.get("timelist");
                    List<Object[]> excellist = (List<Object[]>) map.get("excellist");
                    List<Accvoucher> coutnoIdList = (List<Accvoucher>) map.get("maxInoId");
                    Map<String, Object> kjQjMap = (HashMap<String, Object>) map.get("kuaiJiQiJians");
                    //List<SysPeriod> priedList = (List<SysPeriod>)kjQjMap.get("list");// ??????db??????????????????
                    HashSet<CodeKemu> kemuList = (HashSet<CodeKemu>) map.get("kemuList");
                    List<Accvoucher> saveList = new ArrayList();
                    String[] systemTitleNames = titlesAR.get();
                    int dateIndex = -1;
                    int numberIndex = -1;
                    int idocIndex = -1; //?????????
                    int cdigestIndex = -1; //????????????
                    int ccodeIndex = -1; //????????????
                    int ccodeNameIndex = -1; //????????????
                    int mdIndex = -1; //????????????
                    int mcIndex = -1; // ????????????
                    int cashProjectIndex = -1; //????????????????????????
                    int cdeptIdIndex = -1; //????????????
                    int cpersonIdIndex = -1; //????????????
                    int ccusIdIndex = -1; //????????????
                    int csupIdIndex = -1; //???????????????
                    int projectClassIdIndex = -1; //??????????????????
                    int projectIdIndex = -1; //????????????
                    int cbillIndex = -1; //?????????
                    int ccheckIndex = -1; // ?????????
                    int cdirectorIndex = -1; // ?????????
                    int ccashierIndex = -1; //?????????
                    int iflagIndex = -1;//  ????????????
                    int cbookIndex = -1; //??????
                    int csignIndex = -1; //????????????
                    int settlementMethodIndex = -1; //??????????????????
                    int unitMeasurementIndex = -1; //????????????
                    int ndSIndex = -1; //????????????
                    int ncSIndex = -1; //????????????
                    int unitPriceIndex = -1; //??????
                    int foreignCurrencyIndex = -1; //????????????
                    int nfratMdIndex = -1; //'??????????????????'
                    int nfratMcIndex = -1; //'??????????????????'
                    int mdFIndex = -1; //????????????
                    int mcFIndex = -1; //????????????
                    int pjIdIndex = -1; //?????????
                    int pjDateIndex = -1; //????????????
                    int pjUnitNameIndex = -1; //????????????
                    int cdfine1Index = -1; //?????????1
                    String thisImportDate = DateUtil.today();
                    int sameDateNumber = 0;
                    String sameDateStr = "";
                    for (int i = 0; i < timelist.size(); i++) {
                        // ?????????
                        String uniqueCode = IdUtil.objectId();
                        int inidNumber = 0;
                        String thisCode = "";
                        String coutnoId = "";
                        if (!parms[4].equals("1")) { //?????????
                            // ????????????????????????
                            String importDate = timelist.get(i).get("time").toString().split(">>")[0].substring(0, 7);
                            if (!sameDateStr.equals(importDate)) {
                                sameDateNumber = 0;
                                sameDateStr = importDate;
                            } else {
                                sameDateNumber++;
                            }
                            coutnoId = getThisDateMaxVoucherIdon(coutnoIdList, timelist.get(i).get("time").toString());
             /* if ((Integer.valueOf(coutnoId) + sameDateNumber + 1) < 10) {
                coutnoId = "000" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
              } else if ((Integer.valueOf(coutnoId) + sameDateNumber + 1) >= 10 && (Integer.valueOf(coutnoId) + sameDateNumber + 1) < 100) {
                coutnoId = "00" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
              } else if ((Integer.valueOf(coutnoId) + sameDateNumber + 1) >= 100 && (Integer.valueOf(coutnoId) + sameDateNumber + 1) < 1000) {
                coutnoId = "0" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
              }*/
                            coutnoId = "" + (Integer.valueOf(coutnoId) + sameDateNumber + 1);
                        }
                        // ??????????????????
                        for (int j = 0; j < excellist.size(); j++) {
                            Object[] row = excellist.get(j);
                            // ????????????????????????????????????
                            if (j == 0 && i == 0) { // ???????????????index
                                for (int k = 0; k < row.length; k++) {
                                    if (systemTitleNames[k].equals("????????????")) dateIndex = k;
                                    if (systemTitleNames[k].equals("?????????")) numberIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) idocIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) cdigestIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) ccodeIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) ccodeNameIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) mdIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) mcIndex = k;
                                    if (systemTitleNames[k].equals("????????????????????????")) cashProjectIndex = k;

                                    if (systemTitleNames[k].equals("????????????")) cdeptIdIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) cpersonIdIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) ccusIdIndex = k;
                                    if (systemTitleNames[k].equals("???????????????")) csupIdIndex = k;
                                    if (systemTitleNames[k].equals("??????????????????")) projectClassIdIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) projectIdIndex = k;

                                    if (systemTitleNames[k].equals("?????????")) cbillIndex = k;
                                    if (systemTitleNames[k].equals("?????????")) ccheckIndex = k;
                                    if (systemTitleNames[k].equals("?????????")) cbookIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) iflagIndex = k;
                                    if (systemTitleNames[k].equals("???????????????")) cdirectorIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) csignIndex = k;
                                    if (systemTitleNames[k].equals("???????????????")) ccashierIndex = k;

                                    if (systemTitleNames[k].equals("??????????????????")) settlementMethodIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) unitMeasurementIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) ndSIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) ncSIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) foreignCurrencyIndex = k;
                                    if (systemTitleNames[k].equals("??????????????????")) nfratMdIndex = k;
                                    if (systemTitleNames[k].equals("??????????????????")) nfratMcIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) mdFIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) mcFIndex = k;
                                    if (systemTitleNames[k].equals("??????")) unitPriceIndex = k;
                                    if (systemTitleNames[k].equals("?????????")) pjIdIndex = k;
                                    if (systemTitleNames[k].equals("????????????")) pjDateIndex = k;
                                    if (systemTitleNames[k].equals("??????????????????")) pjUnitNameIndex = k;
                                    if (systemTitleNames[k].equals("?????????1")) cdfine1Index = k;
                                }
                            }
                            // ??????????????????  ?????????????????????  ?????? ??????????????????  ????????????????????????
                            String importDate = timelist.get(i).get("time").toString().split(">>")[0];
                            String tDate = DateUtil.formatDate(DateUtil.parseDate(row[dateIndex].toString()));
                            if (importDate.equals(tDate) && (/*!parms[4].equals("1") || */timelist.get(i).get("time").toString().split(">>")[1].equals(row[numberIndex]))) {
                                String pingZhengNumber = (parms[4].equals("1")) ? getIntegerValue(StrUtil.trim(excellist.get(j)[numberIndex].toString())) : coutnoId.trim();
                                //if (!parms[4].equals("1") && !pingZhengNumber.equals("")) break;
                                String time = timelist.get(i).get("time").toString().replaceAll("-", "");
                                // ??????????????????????????????
                                if (!thisCode.equals(pingZhengNumber)) {
                                    inidNumber = 0;
                                    thisCode = pingZhengNumber;
                                } else {
                                    inidNumber += 1;
                                }
                                String tYear = time.substring(0, 4);
                                String tMonth = time.substring(4, 6);
                                String tQj = ((HashMap<String, String>) kjQjMap.get(tYear)).get(tMonth);//???????????????????????? ?????????
                                Accvoucher accvoucher = new Accvoucher() //??????
                                        .setUniqueCode(uniqueCode).setVouchUnCode(IdUtil.objectId()).setIfrag(setVoucherStauts(setColValue(row, iflagIndex))).setCsign(setColValue(row, csignIndex)).setIyear(StrUtil.trim(tYear)).setImonth(StrUtil.trim(tMonth)).setIyperiod(tYear + tQj) // ????????????
                                        .setIperiod(tQj) //??????
                                        .setCbill(setColValue(row, cbillIndex)).setDbillDate(StrUtil.trim(importDate)).setInoId(StrUtil.trim(pingZhengNumber)).setInid("" + (inidNumber + 1)).setIdoc(setVoucherIdoc(setColValue(row, idocIndex))).setCdigest(setColValue(row, cdigestIndex)).setCcode(setColValue(row, ccodeIndex)).setCcodeName(setColValue(row, ccodeNameIndex)).setMd(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, mdIndex)), 2)).setMc(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, mcIndex)), 2)).setCsign(setColValue(row, csignIndex)).setCdirector(setColValue(row, cdirectorIndex)).setCashProject(setColValue(row, cashProjectIndex)).setPjCsettle(setColValue(row, settlementMethodIndex)).setPjId(setColValue(row, pjIdIndex)).setPjDate(setColValue(row, pjDateIndex)).setPjUnitName(setColValue(row, pjUnitNameIndex));
                                // ????????????
                                if (StringUtils.isNotBlank(setColValue(row, ccheckIndex)))
                                    accvoucher.setCcheck(setColValue(row, ccheckIndex)).setCcheckDate(thisImportDate);
                                if (StringUtils.isNotBlank(setColValue(row, ccashierIndex)))
                                    accvoucher.setCcashier(setColValue(row, ccashierIndex)).setCcashierDate(thisImportDate);
                                if (StringUtils.isNotBlank(setColValue(row, cbookIndex)))
                                    accvoucher.setCbook(setColValue(row, cbookIndex)).setIbook("1").setIbookDate(thisImportDate);
                                CodeKemu thisKemu = getThisKemu(setColValue(row, ccodeIndex), kemuList);
                                if (thisKemu.getBdept().equals("1"))
                                    accvoucher.setCdeptId(setColValue(row, cdeptIdIndex));
                                if (thisKemu.getBperson().equals("1"))
                                    accvoucher.setCpersonId(setColValue(row, cpersonIdIndex));
                                if (thisKemu.getBcus().equals("1")) accvoucher.setCcusId(setColValue(row, ccusIdIndex));
                                if (thisKemu.getBsup().equals("1")) accvoucher.setCsupId(setColValue(row, csupIdIndex));
                                if (thisKemu.getBitem().equals("1"))
                                    accvoucher.setProjectClassId(setColValue(row, projectClassIdIndex)).setProjectId(setColValue(row, projectIdIndex));
                                if (parms[1].equals("1")) {               //??????
                                    accvoucher.setNdS(setColValue(row, ndSIndex).replaceAll(",", "")).setNcS(setColValue(row, ncSIndex).replaceAll(",", "")).setNfratMd(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, nfratMdIndex).replaceAll(",", "")), 2)).setNfratMc(NumberUtil.roundStr(checkNumberIsBlank(setColValue(row, nfratMcIndex).replaceAll(",", "")), 2)).setCunitPrice(setColValue(row, unitPriceIndex)).setMdF(setColValue(row, mdFIndex))
                                    //.setMcF(setColValue(row, mcFIndex))
                                    ;
                                    if (false) {// ?????????????????????????????? ?????? ??? ???????????? ??????
                                        String ccode = accvoucher.getCcode();
                                        accvoucher.setUnitMeasurement(setColValue(row, unitMeasurementIndex)).setForeignCurrency(setColValue(row, foreignCurrencyIndex));
                                    }
                                }
                                // ??????????????????????????? ?????????????????????????????????
                                // if (systemTitleNames[k].equals("?????????1"))cdfine1Index = k;
                                if (systemTitleNames.toString().contains("?????????")) { // ???????????????
                                    List<Integer> keys = new ArrayList<>();
                                    List<Integer> indexs = new ArrayList<>();
                                    for (int k = 0; k < systemTitleNames.length; k++) {
                                        String titleName = systemTitleNames[k];
                                        String numStr = titleName.replace("?????????", "");
                                        if (titleName.startsWith("?????????") && NumberUtil.isNumber(numStr)) {
                                            keys.add(Integer.parseInt(numStr));
                                            indexs.add(k);
                                        }
                                    }
                                    if (keys.size() > 30) {
                                        return Mono.just(R.ok().setResult("?????????????????????????????????????????????????????????30????????????").setCode(404L));
                                    } else if (keys.size() > 0) {
                                        accvoucher = service.modifyPingZhengEntityPropertyByAuxiliaryItem(accvoucher, excellist.get(j), keys, indexs);
                                    }
                                }
                                saveList.add(accvoucher);
                            }
                        }
                    }
                    //return Flux.just(new Accvoucher());
                    return accvoucherRepository.saveAll(saveList);
                }).collectList().flatMap(list -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(list)).map(o -> R.ok().setResult(o));
    }

    @Transactional
    @PostMapping("/importAccvoucherQiChu") // ??????????????????
    public Mono<R> importAccvoucherQiChu(@RequestPart("file") FilePart filePartParm, @RequestPart("templateInfo") String templateID) throws Exception {
        // ????????? -- ???????????? -- ??????????????? -- ???????????? -- ?????????????????????--??????accId--year
        System.out.println(templateID);
        String[] parms = templateID.split("--");
        AtomicReference<String[]> titlesAR = new AtomicReference(); // ????????????????????????
        AtomicReference<String> thisDbName = new AtomicReference(parms[5]); // ??????????????????
        Path tempFilePath = Files.createTempFile("", new String(filePartParm.filename().getBytes("ISO-8859-1"), "UTF-8"));
        return Mono.just(filePartParm).flatMap(files -> taskRepository.save(new Task().setCaozuoUnique("test001").setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(DateUtil.thisMonth() + "").setMethod("??????")).map(entity1 -> files)).flatMap(filePart -> {
                    try {
                        return DataBufferUtils.write(filePart.content(), AsynchronousFileChannel.open(tempFilePath, StandardOpenOption.WRITE), 0).doOnComplete(() -> log.info("????????????")).collectList().map(item -> tempFilePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return Mono.just("");
                }).flatMap(t -> {
                    return fuzhuHesuanRepository.findAll().collectList();
                })
                // ????????????excel??????
                .flatMap(fzs -> { //??????????????????????????????
                    List<Object[]> list = null;
                    String[] titles = new String[49];
                    titles[0] = "????????????";
                    titles[1] = "????????????";
                    titles[2] = "????????????";
                    titles[3] = "????????????";
                    titles[4] = "????????????";
                    titles[5] = "????????????";
                    titles[6] = "????????????";
                    titles[7] = "????????????";
                    titles[8] = "???????????????";
                    titles[9] = "???????????????";
                    titles[10] = "??????????????????";
                    titles[11] = "??????????????????";
                    titles[12] = "????????????";
                    titles[13] = "????????????";
                    titles[14] = "??????";
                    titles[15] = "????????????";
                    titles[16] = "??????";
                    titles[17] = "????????????";
                    titles[18] = "????????????";
                    titles[19] = "?????????1";
                    titles[20] = "?????????2";
                    titles[21] = "?????????3";
                    titles[22] = "?????????4";
                    titles[23] = "?????????5";
                    titles[24] = "?????????6";
                    titles[25] = "?????????7";
                    titles[26] = "?????????8";
                    titles[27] = "?????????9";
                    titles[28] = "?????????10";
                    titles[29] = "?????????11";
                    titles[30] = "?????????12";
                    titles[31] = "?????????13";
                    titles[32] = "?????????14";
                    titles[33] = "?????????15";
                    titles[34] = "?????????16";
                    titles[35] = "?????????17";
                    titles[36] = "?????????18";
                    titles[37] = "?????????19";
                    titles[38] = "?????????20";
                    titles[39] = "?????????21";
                    titles[40] = "?????????22";
                    titles[41] = "?????????23";
                    titles[42] = "?????????24";
                    titles[43] = "?????????25";
                    titles[44] = "?????????26";
                    titles[45] = "?????????27";
                    titles[46] = "?????????28";
                    titles[47] = "?????????29";
                    titles[48] = "?????????30";
                    fzs.forEach(fz -> {
                        titles[19 - 1 + fz.getCdfine()] = fz.getCname();
                    });
                    if (null == titlesAR.get()) titlesAR.set(new String[titles.length - 1]);
                    titlesAR.set(titles.clone());
                    try {
                        list = new XlsUtils3(tempFilePath.toString()).getExcelObj(tempFilePath.toString(), titles, 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        assert tempFilePath != null;
                        try {
                            Files.delete(tempFilePath);
                        } catch (IOException e) {
                            System.err.println("???????????????????????????????????????: ---------???---------");
                            e.printStackTrace();
                        }
                    }
                    return Mono.just(list);
                })
                // ??????????????????
                .flatMap(list -> {
                    Map mapArr = new HashMap();
                    mapArr.put("excellist", list);   // excel?????????
                    mapArr.put("error", "");
                    mapArr.put("code", "200");

                    String finalThisImportYearStr = templateID.split("--")[6];
                    return Mono.just("").map(s -> {
                        //?????????????????????????????????
                        Mono<HashSet<String[]>> all = codeKemuRepository.findAllByYear(finalThisImportYearStr).collectList().map(list1 -> new HashSet<>(getHashSetByKeMu(list1)));
                        Mono<HashSet<CodeKemu>> lastStage = codeKemuRepository.findAllByYearAndBendQiChu(finalThisImportYearStr).collectList().map(list1 -> new HashSet<>(list1));
                        // ??????????????????
                        Mono<List<SysPsn>> geSets = psnRepository.findAllPsnCodeOrPsnNameByFlag().collectList();
                        Mono<List<SysDepartment>> bmSets = departmentRepository.findAllDeptCodeOrDeptNameByFlag().collectList();
                        Mono<List<Customer>> khSets = customerRepository.findAllCustCodeOrCustNameByFlag().collectList();
                        Mono<List<Supplier>> gysSets = supplierRepository.findAllCustCodeOrCustNameByFlag().collectList();
                        Mono<List<ProjectCategory>> proClassSets = projectCategoryRepository.findProjectCateCodeOrProjectCateNameByFlag().collectList();
                        Mono<List<Project>> proSets = projectRepository.findAllProCodeOrProNameByFlag().collectList();
                        Mono<List<ProjectItem>> proItSets = projectItemRepository.findAllItemCodeOrItemNameByAll().collectList();

                        return Mono.zip(all, lastStage, geSets, bmSets, khSets, gysSets, proClassSets, proSets);
                    }).flatMap(zips -> {
                        return zips.map(many -> {
                            mapArr.put("codeSets", many);
                            return mapArr;
                        });
                    });
                })
                // ????????????????????????????????????????????????????????????????????????
                .flatMap(map -> {
                    String error = (String) map.get("error");
                    Map<String, Object> mapArr = new HashMap<>();
                    List<SubjectInitialBalanceVo> accvoList = new ArrayList<>();
                    if (StringUtils.isNotBlank(error)) {
                        mapArr = new HashMap<>();
                        mapArr.put("error", error);
                        mapArr.put("code", "404");
                        return Mono.just(mapArr);
                    }
                    List<Object[]> list = (List<Object[]>) map.get("excellist");
                    // ???????????? ??? ????????????
                    Tuple8<HashSet<String[]>, HashSet<CodeKemu>, List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, List<ProjectCategory>, List<Project>> beingSets = (Tuple8<HashSet<String[]>, HashSet<CodeKemu>, List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, List<ProjectCategory>, List<Project>>) map.get("codeSets");

                    boolean fx = "1".equals(parms[2]);

                    for (int i = 0; i < list.size(); i++) {
                        Object[] row = (Object[]) list.get(i);
                        String codeValue = row[0].toString();
                        HashSet<CodeKemu> kemuList = beingSets.getT2();
                        CodeKemu codeKemu = getThisKemu(codeValue, kemuList);
                        SubjectInitialBalanceVo accvo = getThisSubjectInitialBalanceVo(codeKemu);
                        mapArr = new HashMap<>();
                        String codeErrorStr = "excel????????? " + (i + 1) + " ?????????????????????" + codeValue + "???";
                        if (!checkImportKemuExist(beingSets.getT1(), codeValue, "2")) {
                            mapArr.put("error", codeErrorStr + "????????????????????????????????????????????????????????????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        }
                        if (null == getThisKemu(codeValue, beingSets.getT2())) {
                            mapArr.put("error", codeErrorStr + "???????????????????????????????????????????????????????????????????????????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        }

                        //????????????????????????????????????mdmc
                        //titles[17] = "????????????";titles[18] = "????????????";
                        Object mdTemp = row[17];
                        Object mcTemp = row[18];
                        if (mdTemp == null) {
                            mapArr.put("error", codeErrorStr + "?????????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        }
                        if (mcTemp == null) {
                            mapArr.put("error", codeErrorStr + "?????????????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        }
                        BigDecimal mdBig = BigDecimal.valueOf(0.0);
                        BigDecimal mcBig = BigDecimal.valueOf(0.0);
                        if (!"".equals(mdTemp)) {
                            try {
                                mdBig = (BigDecimal) mdTemp;
                            } catch (Exception e) {
                                mapArr.put("error", codeErrorStr + "?????????????????????????????????,?????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                        }
                        if (!"".equals(mcTemp)) {
                            try {
                                mcBig = (BigDecimal) mcTemp;
                            } catch (Exception e) {
                                mapArr.put("error", codeErrorStr + "?????????????????????????????????,?????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                        }

                        mdBig = fmtJiShui(mdBig, 2);
                        mcBig = fmtJiShui(mcBig, 2);

                        if (mdBig != BigDecimal.valueOf(0.0) && mcBig != BigDecimal.valueOf(0.0)) {
                            mapArr.put("error", codeErrorStr + "????????????????????????????????????,???????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        }
                        if (mdBig == BigDecimal.valueOf(0.0) && mcBig == BigDecimal.valueOf(0.0)) {
                            mapArr.put("error", codeErrorStr + "??????????????????????????????,???????????????????????????");
                            mapArr.put("code", "404");
                            return Mono.just(mapArr);
                        }

                        accvo.setMd(mdBig);
                        accvo.setMc(mcBig);

                        //????????????????????????
                        if (accvo.getBperson().equals("1")) {
                            Object grValue = fx ? row[3] : row[2];
                            List<SysPsn> psnCollect = beingSets.getT3().stream().filter(item -> (fx ? item.getPsnName().equals(grValue) : item.getPsnCode().equals(grValue))).collect(Collectors.toList());
                            if (grValue == null || "".equals(grValue)) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (psnCollect.size() == 0) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + grValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else {
                                accvo.setCpersonId(psnCollect.get(0).getUniqueCode());
                            }
                        }

                        // ????????????????????????
                        if (accvo.getBdept().equals("1")) {
                            Object dValue = fx ? row[5] : row[4];
                            List<SysDepartment> deptCollect = beingSets.getT4().stream().filter(item -> (fx ? item.getDeptName().equals(dValue) : item.getDeptCode().equals(dValue))).collect(Collectors.toList());
                            if (dValue == null || "".equals(dValue)) {
                                mapArr.put("error", codeErrorStr + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (deptCollect.size() == 0) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + dValue.toString() + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else {
                                accvo.setCdeptId(deptCollect.get(0).getUniqueCode());
                            }
                        }

                        // ????????????????????????
                        if (accvo.getBcus().equals("1")) {
                            Object cValue = fx ? row[7] : row[6];
                            List<Customer> customerCollect = beingSets.getT5().stream().filter(item -> (fx ? (item.getCustName().equals(cValue) || item.getCustAbbname().equals(cValue)) : item.getCustCode().equals(cValue))).collect(Collectors.toList());
                            if (cValue == null || "".equals(cValue)) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (customerCollect.size() == 0) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + cValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else {
                                accvo.setCcusId(customerCollect.get(0).getUniqueCode());
                            }
                        }

                        // ???????????????????????????
                        if (accvo.getBsup().equals("1")) {
                            Object gyValue = fx ? row[9] : row[8];
                            List<Supplier> supCollect = beingSets.getT6().stream().filter(item -> (fx ? (item.getCustName().equals(gyValue) || item.getCustAbbname().equals(gyValue)) : item.getCustCode().equals(gyValue))).collect(Collectors.toList());
                            if (gyValue == null || "".equals(gyValue)) {
                                mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (supCollect.size() == 0) {
                                mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + gyValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else {
                                accvo.setCsupId(supCollect.get(0).getUniqueCode());
                            }
                        }

                        // ????????????????????????
                        if (accvo.getBitem().equals("1")) {
                            Object pdValue = fx ? row[11] : row[10];
                            Object pValue = fx ? row[13] : row[12];
                            String proClassNum = "";
                            List<ProjectCategory> proClassCollect = beingSets.getT7().stream().filter(item -> (fx ? item.getProjectCateName().equals(pdValue) : item.getProjectCateCode().equals(pdValue))).collect(Collectors.toList());
                            List<Project> proCollect = beingSets.getT8().stream().filter(item -> (fx ? (item.getProjectName().equals(pValue)) : item.getProjectCode().equals(pValue))).collect(Collectors.toList());
                            if (pValue == null || "".equals(pValue)) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (pdValue == null || "".equals(pdValue)) {
                                mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????" + (fx ? "??????" : "??????") + "?????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (proCollect.size() == 0) {
                                mapArr.put("error", codeErrorStr + codeValue + "????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + pValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else if (proClassCollect.size() == 0) {
                                mapArr.put("error", codeErrorStr + codeValue + "??????????????????????????????????????????" + (fx ? "??????" : "??????") + "??????" + pdValue + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            } else {
                                ProjectCategory pc = proClassCollect.get(0);
                                Project pro = proCollect.get(0);
                                if (pc.getProjectCateCode().equals(pro.getProjectCateCode())) {
                                    accvo.setProjectId(pro.getUniqueCode());
                                    accvo.setProjectClassId(pc.getProjectCateCode());
                                }
                            }
                        }

                        // titles[14] = "??????";
                        // ????????????????????????[?????????????????????????????????????????????????????????]
                        if (accvo.getBnum().equals("1")) {
                            Object numTemp = row[14];
                            if (numTemp == null) {
                                mapArr.put("error", codeErrorStr + "????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            BigDecimal numBig = BigDecimal.valueOf(0.0);
                            if (!"".equals(numTemp)) {
                                try {
                                    numBig = (BigDecimal) numTemp;
                                } catch (Exception e) {
                                    mapArr.put("error", codeErrorStr + "???????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }

                            numBig = fmtJiShui(numBig, 2);

                            if (numBig.compareTo(BigDecimal.valueOf(0)) == 0) {
                                mapArr.put("error", codeErrorStr + "??????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            BigDecimal comPare0 = BigDecimal.valueOf(0.0);
                            BigDecimal md = accvo.getMd();
                            BigDecimal mc = accvo.getMc();

                            BigDecimal nds = BigDecimal.valueOf(0);
                            BigDecimal ncs = BigDecimal.valueOf(0);

                            if (md.compareTo(comPare0) != 0) {
                                nds = numBig;
                            }
                            if (mc.compareTo(comPare0) != 0) {
                                ncs = numBig;
                            }

                            if (md.compareTo(comPare0) != 0) {
                                if ((md.compareTo(comPare0) == 1 && nds.compareTo(comPare0) == -1) || (md.compareTo(comPare0) == -1 && nds.compareTo(comPare0) == 1)) {
                                    mapArr.put("error", codeErrorStr + "??????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }

                            if (mc.compareTo(comPare0) != 0) {
                                if ((mc.compareTo(comPare0) == 1 && ncs.compareTo(comPare0) == -1) || (mc.compareTo(comPare0) == -1 && ncs.compareTo(comPare0) == 1)) {
                                    mapArr.put("error", codeErrorStr + "??????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }
                            accvo.setNdS(nds);
                            accvo.setNcS(ncs);
                        }

                        //titles[15] = "????????????";titles[16] = "??????";
                        // ????????????????????????[???????????????????????????????????????????????????????????????]
                        if (accvo.getCurrency().equals("1")) {//???????????????????????? ?????? ??? ?????? ????????????
                            Object moneyTemp = row[15];
                            Object hlTemp = row[16];
                            if (moneyTemp == null) {
                                mapArr.put("error", codeErrorStr + "??????????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            if (hlTemp == null) {
                                mapArr.put("error", codeErrorStr + "????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            BigDecimal moneyBig = BigDecimal.valueOf(0.0);
                            BigDecimal hlBig = BigDecimal.valueOf(0.0);
                            if (!"".equals(moneyTemp)) {
                                try {
                                    moneyBig = (BigDecimal) moneyTemp;
                                } catch (Exception e) {
                                    mapArr.put("error", codeErrorStr + "?????????????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }

                            if (!"".equals(hlTemp)) {
                                try {
                                    hlBig = (BigDecimal) hlTemp;
                                } catch (Exception e) {
                                    mapArr.put("error", codeErrorStr + "???????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }

                            moneyBig = fmtJiShui(moneyBig, 6);
                            hlBig = fmtJiShui(hlBig, 6);

                            if (moneyBig.compareTo(BigDecimal.valueOf(0)) == 0) {
                                mapArr.put("error", codeErrorStr + "????????????????????????????????????????????????????????????????????????");
                                mapArr.put("code", "404");
                                return Mono.just(mapArr);
                            }
                            BigDecimal comPare0 = BigDecimal.valueOf(0.0);
                            BigDecimal md = accvo.getMd();
                            BigDecimal mc = accvo.getMc();

                            BigDecimal nfratMd = BigDecimal.valueOf(0);
                            BigDecimal nfratMc = BigDecimal.valueOf(0);

                            if (md.compareTo(comPare0) != 0) {
                                nfratMd = moneyBig;
                            }
                            if (mc.compareTo(comPare0) != 0) {
                                nfratMc = moneyBig;
                            }

                            if (md.compareTo(comPare0) != 0) {
                                if ((md.compareTo(comPare0) == 1 && nfratMd.compareTo(comPare0) == -1) || (md.compareTo(comPare0) == -1 && nfratMc.compareTo(comPare0) == 1)) {
                                    mapArr.put("error", codeErrorStr + "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }

                            if (mc.compareTo(comPare0) != 0) {
                                if ((mc.compareTo(comPare0) == 1 && nfratMd.compareTo(comPare0) == -1) || (mc.compareTo(comPare0) == -1 && nfratMc.compareTo(comPare0) == 1)) {
                                    mapArr.put("error", codeErrorStr + "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                                    mapArr.put("code", "404");
                                    return Mono.just(mapArr);
                                }
                            }
                            accvo.setNfratMd(nfratMd);
                            accvo.setNfratMc(nfratMc);
                            accvo.setNfrat(hlBig);
                        }

                        accvoList.add(accvo);
                    }
                    mapArr.put("accvoList", accvoList);
                    return Mono.just(mapArr);
                }).flatMapMany(map -> {
                    String error = (String) map.get("error");
                    String code = (String) map.get("code");
                    List<SubjectInitialBalanceVo> accvoList = (List<SubjectInitialBalanceVo>) map.get("accvoList");
                    if (StringUtils.isNotBlank(error)) {
                        return Mono.just(R.ok().setResult(error).setCode(Long.valueOf(code)));
                    }
                    // ??????????????????
                    List<Accvoucher> saveList = new ArrayList<>();
                    for (int i = 0; i < accvoList.size(); i++) {
                        SubjectInitialBalanceVo vo = accvoList.get(i);
                        Accvoucher acc = new Accvoucher();
                        acc.setIyear(vo.getIyear());
                        acc.setImonth(vo.getIperiod());
                        acc.setIyperiod(vo.getIyperiod());
                        acc.setCcode(vo.getCcode());
                        acc.setCcodeName(vo.getCcodeName());
                        acc.setMd(vo.getMd().toString());
                        acc.setMc(vo.getMc().toString());
                        if ("1".equals(vo.getBnum())) {
                            acc.setNdS(vo.getNdS().toString());
                            acc.setNcS(vo.getNcS().toString());
                            acc.setCunitPrice(vo.getMd().add(vo.getMc()).divide(vo.getNdS().add(vo.getNcS())).toString());
                        }
                        if ("1".equals(vo.getCurrency())) {
                            acc.setNfratMd(vo.getNfratMd().toString());
                            acc.setNfratMc(vo.getNfratMc().toString());
                            acc.setMdF(vo.getNfrat().toString());
                        }
                        if ("1".equals(vo.getBperson())) {
                            acc.setCpersonId(vo.getCpersonId());
                        }
                        if ("1".equals(vo.getBdept())) {
                            acc.setCdeptId(vo.getCdeptId());
                        }
                        if ("1".equals(vo.getBcus())) {
                            acc.setCcusId(vo.getCcusId());
                        }
                        if ("1".equals(vo.getBsup())) {
                            acc.setCsupId(vo.getCsupId());
                        }
                        if ("1".equals(vo.getBitem())) {
                            acc.setProjectClassId(vo.getProjectClassId());
                            acc.setProjectId(vo.getProjectId());
                        }

                        if ("1".equals(vo.getCdfine1())) {
                            acc.setCdfine1(vo.getCdfine1Id());
                        }
                        if ("1".equals(vo.getCdfine2())) {
                            acc.setCdfine2(vo.getCdfine2Id());
                        }
                        if ("1".equals(vo.getCdfine3())) {
                            acc.setCdfine3(vo.getCdfine3Id());
                        }
                        if ("1".equals(vo.getCdfine4())) {
                            acc.setCdfine4(vo.getCdfine4Id());
                        }
                        if ("1".equals(vo.getCdfine5())) {
                            acc.setCdfine5(vo.getCdfine5Id());
                        }
                        if ("1".equals(vo.getCdfine6())) {
                            acc.setCdfine6(vo.getCdfine6Id());
                        }
                        if ("1".equals(vo.getCdfine7())) {
                            acc.setCdfine7(vo.getCdfine7Id());
                        }
                        if ("1".equals(vo.getCdfine8())) {
                            acc.setCdfine8(vo.getCdfine8Id());
                        }
                        if ("1".equals(vo.getCdfine9())) {
                            acc.setCdfine9(vo.getCdfine9Id());
                        }

                        if ("1".equals(vo.getCdfine10())) {
                            acc.setCdfine10(vo.getCdfine10Id());
                        }
                        if ("1".equals(vo.getCdfine11())) {
                            acc.setCdfine11(vo.getCdfine11Id());
                        }
                        if ("1".equals(vo.getCdfine12())) {
                            acc.setCdfine12(vo.getCdfine12Id());
                        }
                        if ("1".equals(vo.getCdfine13())) {
                            acc.setCdfine13(vo.getCdfine13Id());
                        }
                        if ("1".equals(vo.getCdfine14())) {
                            acc.setCdfine14(vo.getCdfine14Id());
                        }
                        if ("1".equals(vo.getCdfine15())) {
                            acc.setCdfine15(vo.getCdfine15Id());
                        }
                        if ("1".equals(vo.getCdfine16())) {
                            acc.setCdfine16(vo.getCdfine16Id());
                        }
                        if ("1".equals(vo.getCdfine17())) {
                            acc.setCdfine17(vo.getCdfine17Id());
                        }
                        if ("1".equals(vo.getCdfine18())) {
                            acc.setCdfine18(vo.getCdfine18Id());
                        }
                        if ("1".equals(vo.getCdfine19())) {
                            acc.setCdfine19(vo.getCdfine19Id());
                        }

                        if ("1".equals(vo.getCdfine20())) {
                            acc.setCdfine20(vo.getCdfine20Id());
                        }
                        if ("1".equals(vo.getCdfine21())) {
                            acc.setCdfine21(vo.getCdfine21Id());
                        }
                        if ("1".equals(vo.getCdfine22())) {
                            acc.setCdfine22(vo.getCdfine22Id());
                        }
                        if ("1".equals(vo.getCdfine23())) {
                            acc.setCdfine23(vo.getCdfine23Id());
                        }
                        if ("1".equals(vo.getCdfine24())) {
                            acc.setCdfine24(vo.getCdfine24Id());
                        }
                        if ("1".equals(vo.getCdfine25())) {
                            acc.setCdfine25(vo.getCdfine25Id());
                        }
                        if ("1".equals(vo.getCdfine26())) {
                            acc.setCdfine26(vo.getCdfine26Id());
                        }
                        if ("1".equals(vo.getCdfine27())) {
                            acc.setCdfine27(vo.getCdfine27Id());
                        }
                        if ("1".equals(vo.getCdfine28())) {
                            acc.setCdfine28(vo.getCdfine28Id());
                        }
                        if ("1".equals(vo.getCdfine29())) {
                            acc.setCdfine29(vo.getCdfine29Id());
                        }
                        if ("1".equals(vo.getCdfine30())) {
                            acc.setCdfine30(vo.getCdfine30Id());
                        }

                        saveList.add(acc);
                    }
                    return Flux.just(new Accvoucher());
                    //return accvoucherRepository.saveAll(saveList);
                }).collectList().flatMap(list -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????????????????").thenReturn(list)).map(o -> R.ok().setResult(o));
    }

    private BigDecimal fmtJiShui(BigDecimal num, Integer xiaoshu) {
        return num.setScale(xiaoshu, BigDecimal.ROUND_HALF_UP);
    }

    private SubjectInitialBalanceVo getThisSubjectInitialBalanceVo(CodeKemu ck) {
        SubjectInitialBalanceVo vo = new SubjectInitialBalanceVo();

        vo.setIyear(ck.getIyear());

        vo.setCcode(ck.getCcode());
        vo.setCcodeName(ck.getCcodeName());

        Integer fuzhus = 0;

        vo.setBnum("0");
        if ("1".equals(ck.getBnum())) vo.setBnum("1");
        vo.setCurrency("0");
        if ("1".equals(ck.getCurrency())) vo.setCurrency("1");

        vo.setBperson("0");
        if ("1".equals(ck.getBperson())) {
            vo.setBperson("1");
            fuzhus = fuzhus + 1;
        }
        vo.setBdept("0");
        if ("1".equals(ck.getBdept())) {
            vo.setBdept("1");
            fuzhus = fuzhus + 1;
        }
        vo.setBcus("0");
        if ("1".equals(ck.getBcus())) {
            vo.setBcus("1");
            fuzhus = fuzhus + 1;
        }
        vo.setBsup("0");
        if ("1".equals(ck.getBsup())) {
            vo.setBsup("1");
            fuzhus = fuzhus + 1;
        }
        vo.setBitem("0");
        if ("1".equals(ck.getBitem())) {
            vo.setBitem("1");
            fuzhus = fuzhus + 1;
        }

        vo.setCdfine1("0");
        if ("1".equals(ck.getCdfine1())) {
            vo.setCdfine1("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine2("0");
        if ("1".equals(ck.getCdfine2())) {
            vo.setCdfine2("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine3("0");
        if ("1".equals(ck.getCdfine3())) {
            vo.setCdfine3("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine4("0");
        if ("1".equals(ck.getCdfine4())) {
            vo.setCdfine4("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine5("0");
        if ("1".equals(ck.getCdfine5())) {
            vo.setCdfine5("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine6("0");
        if ("1".equals(ck.getCdfine6())) {
            vo.setCdfine6("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine7("0");
        if ("1".equals(ck.getCdfine7())) {
            vo.setCdfine7("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine8("0");
        if ("1".equals(ck.getCdfine8())) {
            vo.setCdfine8("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine9("0");
        if ("1".equals(ck.getCdfine9())) {
            vo.setCdfine9("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine10("0");
        if ("1".equals(ck.getCdfine10())) {
            vo.setCdfine10("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine11("0");
        if ("1".equals(ck.getCdfine11())) {
            vo.setCdfine11("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine12("0");
        if ("1".equals(ck.getCdfine12())) {
            vo.setCdfine12("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine13("0");
        if ("1".equals(ck.getCdfine13())) {
            vo.setCdfine13("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine14("0");
        if ("1".equals(ck.getCdfine14())) {
            vo.setCdfine14("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine15("0");
        if ("1".equals(ck.getCdfine15())) {
            vo.setCdfine15("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine16("0");
        if ("1".equals(ck.getCdfine16())) {
            vo.setCdfine16("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine17("0");
        if ("1".equals(ck.getCdfine17())) {
            vo.setCdfine17("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine18("0");
        if ("1".equals(ck.getCdfine18())) {
            vo.setCdfine18("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine19("0");
        if ("1".equals(ck.getCdfine19())) {
            vo.setCdfine19("1");
            fuzhus = fuzhus + 1;
        }

        vo.setCdfine20("0");
        if ("1".equals(ck.getCdfine20())) {
            vo.setCdfine20("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine21("0");
        if ("1".equals(ck.getCdfine21())) {
            vo.setCdfine21("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine22("0");
        if ("1".equals(ck.getCdfine22())) {
            vo.setCdfine22("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine23("0");
        if ("1".equals(ck.getCdfine23())) {
            vo.setCdfine23("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine24("0");
        if ("1".equals(ck.getCdfine24())) {
            vo.setCdfine24("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine25("0");
        if ("1".equals(ck.getCdfine25())) {
            vo.setCdfine25("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine26("0");
        if ("1".equals(ck.getCdfine26())) {
            vo.setCdfine26("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine27("0");
        if ("1".equals(ck.getCdfine27())) {
            vo.setCdfine27("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine28("0");
        if ("1".equals(ck.getCdfine28())) {
            vo.setCdfine28("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine29("0");
        if ("1".equals(ck.getCdfine29())) {
            vo.setCdfine29("1");
            fuzhus = fuzhus + 1;
        }
        vo.setCdfine30("0");
        if ("1".equals(ck.getCdfine30())) {
            vo.setCdfine30("1");
            fuzhus = fuzhus + 1;
        }

        if (fuzhus == 0) {
            vo.setIyperiod(ck.getIyear() + "21");
            vo.setIperiod("21");
        } else {
            vo.setIyperiod(ck.getIyear() + "00");
            vo.setIperiod("00");
        }
        vo.setFuzhu(String.valueOf(fuzhus));
        return vo;
    }

    private String getIntegerValue(String trim) {
        trim = StrUtil.trim(trim);
        if (trim.toCharArray()[0] == -96) {
            trim = getIntegerValue(trim.substring(1));
        }
        return Integer.parseInt(trim) + "";
    }

    private String getThisDateMaxVoucherIdon(List<Accvoucher> coutnoIdList, String time) {
        String[] split = time.split("-");
        for (Accvoucher accvoucher : coutnoIdList) {
            if (accvoucher.getIyear().equals(split[0]) && accvoucher.getImonth().equals(split[1])) {
                return accvoucher.getInoId();
            }
        }
        return "0000";
    }

    private String setVoucherIdoc(String setColValue) {
        String resutl = "";
        if (StrUtil.isNotBlank(setColValue) && NumberUtil.isNumber(setColValue) && Integer.parseInt(setColValue) > 0) {
            resutl = setColValue;
        }
        return resutl;
    }

    private String setVoucherStauts(String setColValue) {
        String resutl = "0";
        if (StrUtil.isNotBlank(setColValue)) {
            switch (setColValue) {
                case ("??????"):
                    resutl = "1";
                    break;

                case ("??????"):
                    resutl = "2";
                    break;

                case ("??????"):
                    resutl = "3";
                    break;
            }
        }
        return resutl;
    }

    private CodeKemu getThisKemu(Object codeValue, HashSet<CodeKemu> kemuList) {
        CodeKemu thisKemu = null;
        for (CodeKemu codeKemu : kemuList) {
            if (codeKemu.getCcode().equals(codeValue)) {
                thisKemu = codeKemu;
                break;
            }
        }
        return thisKemu;
    }

    private String checkNumberIsBlank(String number) {
        if (StringUtils.isBlank(number)) number = "0";
        return number;
    }

    private boolean checkImportKemuExist(HashSet<String[]> t1, Object codeValue, String s) {
        boolean flag = false;
        if (s.equals("2")) {
            for (String[] strings : t1) { //????????????
                if ((strings[0].equals(codeValue) && strings[1].equals("0")) || ((strings[0].equals(codeValue) && strings[1].equals("1")))) {
                    flag = true;
                    break;//??????
                }
            }
        } else {
            for (String[] strings : t1) { //????????????
                if ((strings[0].equals(codeValue) && strings[1].equals("0"))) {
                    flag = true;
                    break;//??????
                }
            }
        }
        return flag;
    }

    private String setColValue(Object[] o, int index) {
        try {
            return null == o[index] ? "" : StrUtil.trim((o[index].toString()));
        } catch (Exception e) {
            return "";
        }
    }

    private List<String[]> getHashSetByKeMu(List<CodeKemu> list1) {
        List<String[]> list = new ArrayList<>(list1.size());
        for (CodeKemu codeKemu : list1) {
            list.add(new String[]{codeKemu.getCcode(), codeKemu.getFlag()});
        }
        return list;
    }

    @PostMapping("/findAllAccvoucher")
    public Mono<R> findAllAccvoucher(@RequestBody Map map) {
        //????????????
        String year = "";
        String[] intervals = null;
        if (map.keySet().size() == 2) {
            return Mono.just("").map(R::ok);
        } else {
            year = map.get("year").toString();
            intervals = JSON.parseArray(map.get("interval").toString()).toArray(new String[0]);
        }
        return accvoucherRepository.findAllPingZhengMingXing(year, intervals[0].trim(), intervals[1].trim()).collectList().map(R::page);
    }

    @GetMapping("/findMaxQj")
    @ApiOperation(value = "????????????????????????", notes = "????????????????????????")
    public Mono<R> findMaxQj() {
        return accvoucherRepository.findFirstByMaxIyperiodValue().map(R::ok).defaultIfEmpty(R.ok().setResult(""));
    }

    @GetMapping("/findMaxQjMonth/{iyear}")
    @ApiOperation(value = "????????????????????????", notes = "????????????????????????")
    public Mono<R> findMaxQj(@PathVariable String iyear) {
        return accvoucherRepository.findFirstByIyearAndImonthBetweenOrderByIyperiodDesc(iyear, "01", "13").map(o -> R.ok(o.getIyperiod())).switchIfEmpty(Mono.just(R.ok().setResult("")));
    }

    @Transactional
    @PostMapping("/findAllPingZhengList")
    public Mono<R> findAllPingZhengList(@RequestBody Map map) {
        //????????????
        if (map.keySet().size() == 2) {
            return Mono.just(R.ok().setResult(CollectOfUtils.mapof("total", 0, "items", new ArrayList<>())));
        }
        String queryMark = map.get("queryMark").toString();
        int page = Integer.parseInt(map.get("page").toString());
        int pageSize = Integer.parseInt(map.get("size").toString());
        Map<String, String> variableMap = ((HashMap<String, HashMap<String, String>>) map.get("condition")).get("variable");
        String intervalStart = variableMap.get("periodStart").replaceAll("-", "");
        String intervalEnd = variableMap.get("periodEnd").replaceAll("-", "");
        String dateStart = variableMap.get("dateStart");
        String dateEnd = variableMap.get("dateEnd");
        Mono<R> rMono = null;
        AtomicReference<Integer> totalAR = new AtomicReference();
        if (StrUtil.isNotBlank(intervalStart) && StrUtil.isNotBlank(intervalEnd)) {
            if (queryMark.equals("1")) {
                rMono = accvoucherRepository.findAllVoucherDetailByIyperiod(intervalStart, intervalEnd).collectList().cache().map(list -> queryFilter(list, map)).map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            } else { // ??????
                rMono = assemblyVoucherPoolOrDetails(intervalStart, intervalEnd,"QJ").map(list -> splitList(queryFilter(list, map), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            }
        } else if (StrUtil.isNotBlank(dateStart) && StrUtil.isNotBlank(dateEnd)) {
            if (queryMark.equals("1")) {
                rMono = accvoucherRepository.findAllVoucherDetailByDate(dateStart.trim(), dateEnd.trim()).collectList().cache().map(list -> queryFilter(list, map)).map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            } else { // ??????
                rMono = assemblyVoucherPoolOrDetails(dateStart.trim(), dateEnd.trim(),"DATE").map(list -> splitList(queryFilter(list, map), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            }
        }
        return rMono;
    }

    private Mono<List<Accvoucher>> assemblyVoucherPoolOrDetails(String intervalStart, String intervalEnd,String mark) {
        Mono<List<Accvoucher>> listMono =  (mark.equals("QJ")?accvoucherRepository.findAllVoucherPoolByIyperiod(intervalStart, intervalEnd):accvoucherRepository.findAllVoucherPoolFastByDate(intervalStart, intervalEnd)).collectList().cache();
        Mono<List<Accvoucher>> listMono1 = (mark.equals("QJ")?accvoucherRepository.findAllVoucherPoolMdByIyperiod(intervalStart, intervalEnd):accvoucherRepository.findAllVoucherPoolMdByDate(intervalStart, intervalEnd)).collectList().cache();
        Mono<List<Accvoucher>> listMono2 = (mark.equals("QJ")?accvoucherRepository.findAllVoucherPoolCdigestByIyperiod(intervalStart, intervalEnd):accvoucherRepository.findAllVoucherPoolCdigestByDate(intervalStart, intervalEnd)).collectList().cache();
        return  Mono.zip(listMono, listMono1, listMono2).flatMap(dbt -> {
           List<Accvoucher> t1 = dbt.getT1();
           List<Accvoucher> t2 = dbt.getT2();
           List<Accvoucher> t3 = dbt.getT3();
           for (Accvoucher accvoucher : t1) {
               for (Accvoucher accvoucher2 : t2) {
                   if (accvoucher.getUniqueCode().equals(accvoucher2.getUniqueCode())) {
                       accvoucher.setMd(accvoucher2.getMd());
                       break;
                   }
               }
               for (Accvoucher accvoucher3 : t3) {
                   if (accvoucher.getUniqueCode().equals(accvoucher3.getUniqueCode())) {
                       accvoucher.setCdigest(accvoucher3.getCdigest());
                       break;
                   }
               }
           }
           return Mono.just(t1);
       });
    }

    @Transactional
    @PostMapping("/findPrintDataByCondition")
    public Mono<R> findPrintDataByCondition(@RequestBody Map map) {
        //????????????
        if (map.keySet().size() != 2) {
            return Mono.just(R.ok().setResult(new ArrayList<>()));
        }
        Map<String, String> constantMap = (HashMap<String, String>) map.get("constant");
        Map<String, Object> variableMap = (HashMap<String, Object>) map.get("variable");
        String queryType = constantMap.get("queryType").toString();
        Mono<R> rMono = null;
        AtomicReference<Integer> totalAR = new AtomicReference();
        if (queryType.equals("1")) {
            if (variableMap.get("printingMethod").toString().equals("1")) {
                List<String> selectKeys = JSON.parseArray(variableMap.get("selectKeys").toString(), String.class);
                rMono = accvoucherRepository.findAllByUniqueCodes(selectKeys).collectList().cache().map(list -> R.ok().setResult(list));
            } else {
                rMono = accvoucherRepository.findAllVoucherDetailByIyperiod("intervalStart", "intervalEnd").collectList().cache().map(list -> R.ok().setResult(list));
            }
        } else {
            String periodStart = variableMap.get("periodStart").toString().replace("-", "");
            String periodEnd = variableMap.get("periodEnd").toString().replace("-", "");
            rMono = accvoucherRepository.findAllVoucherDetailByIyperiod(periodStart, periodEnd).collectList().cache().map(list -> printFilter(list, variableMap)).map(list -> R.ok().setResult(list));
        }
        return rMono;
    }


    @Transactional
    @PostMapping("/findAllCashierPingZhengList")
    public Mono<R> findAllCashierPingZhengList(@RequestBody Map map) {
        //????????????
        String queryMark = map.get("queryMark").toString();
        int page = Integer.parseInt(map.get("page").toString());
        int pageSize = Integer.parseInt(map.get("size").toString());
        Map<String, String> variableMap = ((HashMap<String, HashMap<String, String>>) map.get("condition")).get("variable");
        String intervalStart = variableMap.get("periodStart").replaceAll("-", "");
        String intervalEnd = variableMap.get("periodEnd").replaceAll("-", "");
        ;
        String dateStart = variableMap.get("dateStart");
        String dateEnd = variableMap.get("dateEnd");
        String filterMark = map.get("filterMark").toString();
        Mono<R> rMono = null;
        AtomicReference<Integer> totalAR = new AtomicReference();
        if (StrUtil.isNotBlank(intervalStart) && StrUtil.isNotBlank(intervalEnd)) {
            String thisYear = intervalStart.substring(0, 4);
            if (queryMark.equals("1")) {
                rMono = accvoucherRepository.findAllVoucherDetailByIyperiod(intervalStart, intervalEnd).collectList().flatMap(list -> codeKemuRepository.findAllByYearAndCashierConditions(thisYear).collectList().cache().map(codeList -> filterUnCashierConditionCode(list, codeList, filterMark))).map(list -> queryFilter(list, map)).map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            } else { // ??????
                rMono =  assemblyVoucherPoolOrDetails(intervalStart, intervalEnd,"QJ").flatMap(list -> codeKemuRepository.findAllByYearAndCashierConditions(thisYear).collectList().map(codeList -> filterUnCashierConditionCode(list, codeList, filterMark))).map(list -> splitList(queryFilter(list, map), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            }
        } else if (StrUtil.isNotBlank(dateStart) && StrUtil.isNotBlank(dateEnd)) {
            String thisYear = dateStart.substring(0, 4);
            if (queryMark.equals("1")) {
                rMono = accvoucherRepository.findAllVoucherDetailByDate(dateStart.trim(), dateEnd.trim()).collectList().cache().flatMap(list -> codeKemuRepository.findAllByYearAndCashierConditions(thisYear).collectList().map(codeList -> filterUnCashierConditionCode(list, codeList, filterMark))).map(list -> queryFilter(list, map)).map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            } else { // ??????
                rMono = assemblyVoucherPoolOrDetails(dateStart.trim(), dateEnd.trim(),"DATE").flatMap(list -> codeKemuRepository.findAllByYearAndCashierConditions(thisYear).collectList().map(codeList -> filterUnCashierConditionCode(list, codeList, filterMark))).map(list -> splitList(queryFilter(list, map), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
            }
        }
        return rMono;
    }

    @PostMapping("/findAllAccvoucherXianJin")
    public Mono<R> findAllAccvoucherXianJin(@RequestBody Map map) {
        //????????????
        if (map.keySet().size() == 2) {
            return Mono.just(R.ok().setResult(CollectOfUtils.mapof("total", 0, "items", new ArrayList<>())));
        }
        String queryMark = map.get("queryMark").toString();
        int page = Integer.parseInt(map.get("page").toString());
        int pageSize = Integer.parseInt(map.get("size").toString());
        Map<String, String> variableMap = ((HashMap<String, HashMap<String, String>>) map.get("condition")).get("variable");
        String intervalStart = variableMap.get("periodStart").replaceAll("-", "");
        String intervalEnd = variableMap.get("periodEnd").replaceAll("-", "");
        String accId = map.get("accId").toString();
        map.put("ifrag", "0");
        Mono<R> rMono = null;
        AtomicReference<Integer> totalAR = new AtomicReference();
        AtomicReference<Integer> index = new AtomicReference(1);
        if (StrUtil.isNotBlank(intervalStart) && StrUtil.isNotBlank(intervalEnd)) {
            rMono = accvoucherRepository.findAllVoucherDetailByIyperiodAndTenantId(intervalStart, intervalEnd, accId + "-" + intervalStart.substring(0, 4)).collectList()
                    .map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> {
                        //??????
                        list.forEach(v -> v.setCdfine30(index.getAndSet(index.get() + 1).toString()));
                        return list;
                    }).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
        }
        return rMono;
    }

    @GetMapping("dateTreeXianJin")
    public Mono<R> dateTreeXianJin(String yearMonth) {
        return accvoucherRepository.findAllVoucherTreeByDbillDateLike(yearMonth + "%").collectList().flatMap(alist -> {
            return kmCashFlowRepository.findAll().collectList().map(pList -> {
                List<String> p = pList.stream().map(v -> v.getCcode()).collect(Collectors.toList());
                List<Accvoucher> collect = alist.stream().filter(v -> p.contains((v.getCcode()))).collect(Collectors.toList());
                return collect;
            });
        }).map(list -> {
            Map<String, Map<String, Set<String>>> maps = new HashMap<>();
            for (Accvoucher acc : list) {
                if (maps.containsKey(acc.getCsign())) {
                    Map<String, Set<String>> map = maps.get(acc.getCsign());
                    Set<String> elem = map.containsKey(acc.getDbillDate()) ? map.get(acc.getDbillDate()) : new HashSet<>();
                    elem.add(acc.getInoId());
                    map.put(acc.getDbillDate(), elem);
                    maps.put(acc.getCsign(), map);
                } else {
                    Set<String> elem = new HashSet<>();
                    elem.add(acc.getInoId());
                    maps.put(acc.getCsign(), MapUtil.of(acc.getDbillDate(), elem));
                }
            }
            return maps;
        }).map(o -> R.ok().setResult(o));
    }

    @PostMapping("replacePingZheng")
    @Transactional
    public Mono<R> replacePingZheng(@RequestBody Map map) {
        try {
            if (map == null || map.keySet().size() == 0) return Mono.just(R.error());
            String type = map.get("type").toString();
            String before = map.get("before").toString();
            String after = map.get("after").toString();
            String operator = map.get("operator").toString();
            List<String> keys = (List<String>) map.get("selectedRowKeys");
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodeAndCondition(keys).collectList();
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(operator).setFunctionModule("??????????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                List<Accvoucher> modifyList = new ArrayList<>();
                for (Accvoucher accvoucher : list) {
                    Object value = null;
                    if (type.equals("cdfine")) { // ?????????????????????
                        String cdfineValue = map.get("cdfineValue").toString();
                        value = ReflectionUtil.getValue(accvoucher, cdfineValue);
                    } else {//
                        value = ReflectionUtil.getValue(accvoucher, type);
                    }
                    if (null != value) {
                        if (type.equals("ccode") && value.toString().equals(before.split("-")[0])) { //????????????????????????
                            String[] split = after.split("-");
                            accvoucher.setCcode(split[0]);
                            accvoucher.setCcodeName(split[1]);
                            modifyList.add(accvoucher);
                        } else if (type.equals("cdigest") && value.toString().contains(before)) { //??????????????????
                            String newValue = value.toString().replaceAll(before, after);
                            ReflectionUtil.setValue(accvoucher, type, newValue);
                            modifyList.add(accvoucher);
                        } else if (value.toString().equals(before)) { // ??????
                            ReflectionUtil.setValue(accvoucher, type, after);
                            modifyList.add(accvoucher);
                        }
                    }
                }
                return Mono.just(modifyList);
            }).flatMap(list -> accvoucherRepository.saveAll(list).collectList().map(dbList -> {
                int num = 0;
                if (dbList.size() > 0)
                    num = (new HashSet<>(dbList.stream().map(itm -> itm.getUniqueCode()).collect(Collectors.toList()))).size();
                return R.ok(num);
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????????????????").thenReturn(o));
    }

    @PostMapping("reviewPingZheng")
    @Transactional
    public Mono<R> reviewPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            String taskName = scopeCondition.equals("1") ? "??????" : "??????";
            String reviewMan = map.get("operatorUserName").toString();
            VoucherBusCheckVo reusltVo = new VoucherBusCheckVo();
            List<String> keys = (List<String>) map.get("selectedRowKeys");
            List<String> interval = ListUtil.toList(map.get("thisInterval").toString().split(" ~ "));
            Map<String, Boolean> financialParameters = (Map<String, Boolean>) map.get("financialParameters");
            Boolean selectCashier = financialParameters.get("icashier"); // ????????????????????????
            Boolean selectMaker = financialParameters.get("iverify"); // ????????????????????????
            Boolean selectMakerNo = financialParameters.get("iverifyCancel"); // ?????????????????????
            reusltVo.setSelectNumber(keys.size());
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodeAndCondition(keys).collectList();
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule(taskName + "??????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(taskName);
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                // ????????????????????????
                Mono<List<CodeKemu>> cashiersMono = codeKemuRepository.findAllByYearAndCashierConditions(interval.get(0).substring(0, 4)).collectList();
                // ????????????????????????
                Mono<List<CodeKemu>> lastsMono = codeKemuRepository.findAllByBendAndIyearOrderByCcode("1", interval.get(0).substring(0, 4)).collectList();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0 || !selectCashier) return lastsMono.flatMap(lastList -> {
                    resultMap.put("lastCodeList", lastList);
                    return Mono.just(resultMap);
                });
                return Mono.zip(cashiersMono, lastsMono).flatMap(codeList -> {
                    resultMap.put("cashierCodeList", codeList.getT1());
                    resultMap.put("lastCodeList", codeList.getT2());
                    return Mono.just(resultMap);
                });
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                // ????????????????????????????????????????????????????????????
                List<CodeKemu> cashierCodeList = (List<CodeKemu>) resultMap.get("cashierCodeList");
                List<CodeKemu> lastCodeList = (List<CodeKemu>) resultMap.get("lastCodeList");
                List<Map<String, String>> errorList = new ArrayList<>();
                List<Accvoucher> passList = new ArrayList<>();
                String reviewDate = DateUtil.today();
                String thisCnInoid = "";
                for (Accvoucher accvoucher : voucherList) {
                    List<CodeKemu> kemus = lastCodeList.stream().filter(item -> item.getCcode().equals(accvoucher.getCcode())).collect(Collectors.toList());
                    if (selectCashier && cashierCodeList.size() > 0) {
                        if (StrUtil.isBlank(thisCnInoid) || !thisCnInoid.equals(accvoucher.getInoId())) {
                            thisCnInoid = checkVoucherIsCashierVoucher(voucherList, cashierCodeList, accvoucher);
                        }
                    }
                    if (scopeCondition.equals("1") && selectCashier && StrUtil.isNotBlank(thisCnInoid) && StrUtil.isBlank(accvoucher.getCcashier())) { // ???????????? ????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "6"));
                    } else if (scopeCondition.equals("1") && !selectMaker && accvoucher.getCbill().equals(reviewMan)) { // ???????????????????????????????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "12"));
                    } else if (scopeCondition.equals("0") && selectMakerNo && null != accvoucher.getCcheck() && !accvoucher.getCcheck().equals(reviewMan)) { // ???????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "1"));
                    }/* else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("1")) {
                                errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "2"));
                            }*/ else if (scopeCondition.equals("1") && DateUtil.compare(DateUtil.parse(accvoucher.getDbillDate()), DateUtil.parse(reviewDate)) > 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "3"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("3")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "4"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("2")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "5"));
                    } else if (scopeCondition.equals("1") && kemus.size() == 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "8"));
                    } else if (scopeCondition.equals("1") && ((kemus.get(0).getBdept().equals("1") && StrUtil.isBlank(accvoucher.getCdeptId())) || (kemus.get(0).getBperson().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId())) || (kemus.get(0).getBcus().equals("1") && StrUtil.isBlank(accvoucher.getCcusId())) || (kemus.get(0).getBsup().equals("1") && StrUtil.isBlank(accvoucher.getCsupId()) || (kemus.get(0).getBitem().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId()))))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "9"));
                    } else if ((scopeCondition.equals("1") && StrUtil.isNotBlank(accvoucher.getCcheck())) || (scopeCondition.equals("0") && StrUtil.isBlank(accvoucher.getCcheck()))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", scopeCondition.equals("1") ? "10" : "11"));
                    } else { // ??????
                        passList.add(accvoucher);
                    }
                }
                // ??????????????????
                Set<String> keysets = new HashSet<>();
                for (String thisInfo : new HashSet<>(passList.stream().map(item -> item.getDbillDate().substring(0, 7) + "==" + item.getInoId()).collect(Collectors.toList()))) {
                    double mdSum = 0;
                    double mcSum = 0;
                    Accvoucher thisVoucher = null;
                    String yearMonth = thisInfo.split("==")[0];
                    String thisInoid = thisInfo.split("==")[1];
                    for (Accvoucher accvoucher : passList) {
                        if (ObjectUtil.equal(accvoucher.getDbillDate().substring(0, 7), yearMonth) && ObjectUtil.equal(accvoucher.getInoId(), thisInoid)) {
                            mdSum += StrUtil.isBlank(accvoucher.getMd()) ? 0 : new BigDecimal(accvoucher.getMd()).doubleValue();
                            mcSum += StrUtil.isBlank(accvoucher.getMc()) ? 0 : new BigDecimal(accvoucher.getMc()).doubleValue();
                            if (null == thisVoucher) thisVoucher = accvoucher;
                        }
                    }
                    if (null != thisVoucher && thisInoid.equals(thisVoucher.getInoId())) {
                        if (!NumberUtil.equals(new BigDecimal(mdSum).setScale(2, BigDecimal.ROUND_HALF_UP), new BigDecimal(mcSum).setScale(2, BigDecimal.ROUND_HALF_UP))) {
                            errorList.add(CollectOfUtils.mapof("dbillDate", thisVoucher.getDbillDate(), "inoId", thisInoid, "errorType", "7"));
                        } else {
                            keysets.add(thisVoucher.getUniqueCode());
                        }
                    }
                }
                if (errorList.size() > 0) errorList = removeDuplicateEntries(errorList);
                reusltVo.setSuccessNumber(keysets.size());
                reusltVo.setErrorNumber(errorList.size() > 0 ? new HashSet<>(errorList.stream().map(item -> item.get("dbillDate") + "==" + item.get("inoId")).collect(Collectors.toList())).size() : 0);
                reusltVo.setErrorList(errorList);
                if (keysets.size() == 0)
                    return (scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByCcheckByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByCcheckByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0)).flatMap(num -> {
                        reusltVo.setSuccessNumber(0);
                        reusltVo.setPassNumber(num);
                        return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(R.ok(reusltVo));
                    });
                return (scopeCondition.equals("1") ? accvoucherRepository.reviewVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets, reviewMan, reviewDate) : accvoucherRepository.closeReviewVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets)).collectList().flatMap(ressults -> {
                    reusltVo.setSuccessNumber(keysets.size());
                    return scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByCcheckByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByCcheckByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0);
                }).flatMap(total -> {
                            reusltVo.setPassNumber(total);
                            return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(R.ok(reusltVo));
                        }

                );
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    private List<Map<String, String>> removeDuplicateEntries(List<Map<String, String>> list) {
        List<Map<String, String>> newList = new ArrayList<>();
        String thisInodValue = "";
        for (Map<String, String> map : list) {
            if (!StrUtil.equals(thisInodValue, map.get("inoId"))) {
                thisInodValue = map.get("inoId");
                newList.add(map);
            }
        }
        return newList;
    }

    @PostMapping("reviewPingZhengOld")
    @Transactional
    public Mono<R> reviewPingZhengOld(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Boolean selectCashier = Boolean.valueOf(map.get("selectCashier").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodeAndCondition(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndCondition(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndCondition(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherReviewFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0 || !selectCashier) return Mono.just(resultMap);
                return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList().map(codeList -> {
                    resultMap.put("codeList", codeList);
                    return resultMap;
                });
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0??????????????????"));
                }
                // ????????????????????????????????????????????????????????????
                if (selectCashier) {
                    List<CodeKemu> codeList = (List<CodeKemu>) resultMap.get("codeList");
                    if (codeList.size() > 0) {
                        for (Accvoucher accvoucher : voucherList) {
                            for (CodeKemu codeKemu : codeList) {
                                if (accvoucher.getCcode().equals(codeKemu.getCcode())) {
                                    ERROR_INFO = "???????????????????????????????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "?????????????????????" + "???????????????????????????????????????????????????????????????";
                                    break;
                                }
                            }
                        }
                    }
                }
                if (selectCashier && StrUtil.isNotBlank(ERROR_INFO))
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));
                // ????????????
                Set<String> keys = new HashSet<>();
                String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+ressults.size()+"??????????????????")));*/
                return accvoucherRepository.reviewVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys, reviewMan, reviewDate).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "??????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("closeReviewPingZheng")
    @Transactional
    public Mono<R> closeReviewPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Boolean selectCashier = Boolean.valueOf(map.get("selectCashier").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodeAndConditionClose(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionClose(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionClose(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherReviewFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
                       /* if (list.size() == 0 || !selectCashier)return Mono.just(resultMap);
                        return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList()
                                .map(codeList->{
                                    resultMap.put("codeList",codeList);
                                    return resultMap;
                                });*/
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0??????????????????"));
                }
                       /* // ????????????????????????????????????????????????????????????
                        if (selectCashier){
                            List<CodeKemu>  codeList = (List<CodeKemu>) resultMap.get("codeList");
                            if (codeList.size() > 0){
                                for (Accvoucher accvoucher : voucherList) {
                                    for (CodeKemu codeKemu : codeList) {
                                        if (accvoucher.getCcode().equals(codeKemu.getCcode())){
                                            ERROR_INFO = "???????????????????????????????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"?????????????????????" +
                                                    "???????????????????????????????????????????????????????????????";
                                            break;
                                        }
                                    }
                                }
                            }
                        }*/
                /*if (selectCashier && StrUtil.isNotBlank(ERROR_INFO))return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));*/
                // ????????????
                Set<String> keys = new HashSet<>();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+ressults.size()+"??????????????????")));*/
                return accvoucherRepository.closeReviewVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "??????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }


    @PostMapping("symbolPingZheng")
    @Transactional
    public Mono<R> symbolPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            String taskName = scopeCondition.equals("1") ? "??????" : "??????";
            String reviewMan = map.get("operatorUserName").toString();
            VoucherBusCheckVo reusltVo = new VoucherBusCheckVo();
            List<String> keys = (List<String>) map.get("selectedRowKeys");
            List<String> interval = ListUtil.toList(map.get("thisInterval").toString().split(" ~ "));
            reusltVo.setSelectNumber(keys.size());
            Map<String, Boolean> financialParameters = (Map<String, Boolean>) map.get("financialParameters");
            Boolean selectCashier = financialParameters.get("icashier"); // ????????????????????????
            Boolean selectMakerNo = financialParameters.get("icdirectorNo"); // ?????????????????????
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodeAndConditionSign(keys).collectList();
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("??????" + taskName).setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(taskName);
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                // ????????????????????????
                Mono<List<CodeKemu>> cashiersMono = codeKemuRepository.findAllByYearAndCashierConditions(interval.get(0).substring(0, 4)).collectList();
                // ????????????????????????
                Mono<List<CodeKemu>> lastsMono = codeKemuRepository.findAllByBendAndIyearOrderByCcode("1", interval.get(0).substring(0, 4)).collectList();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0 || !selectCashier) return lastsMono.flatMap(lastList -> {
                    resultMap.put("lastCodeList", lastList);
                    return Mono.just(resultMap);
                });
                return Mono.zip(cashiersMono, lastsMono).flatMap(codeList -> {
                    resultMap.put("cashierCodeList", codeList.getT1());
                    resultMap.put("lastCodeList", codeList.getT2());
                    return Mono.just(resultMap);
                });
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                // ????????????????????????????????????????????????????????????
                List<CodeKemu> cashierCodeList = (List<CodeKemu>) resultMap.get("cashierCodeList");
                List<CodeKemu> lastCodeList = (List<CodeKemu>) resultMap.get("lastCodeList");
                List<Map<String, String>> errorList = new ArrayList<>();
                List<Accvoucher> passList = new ArrayList<>();
                String reviewDate = DateUtil.today();
                String thisCnInoid = "";
                for (Accvoucher accvoucher : voucherList) {
                    List<CodeKemu> kemus = lastCodeList.stream().filter(item -> item.getCcode().equals(accvoucher.getCcode())).collect(Collectors.toList());
                    if (selectCashier && cashierCodeList.size() > 0) {
                        if (StrUtil.isBlank(thisCnInoid) || !thisCnInoid.equals(accvoucher.getInoId())) {
                            thisCnInoid = checkVoucherIsCashierVoucher(voucherList, cashierCodeList, accvoucher);
                        }
                    }
                    if (scopeCondition.equals("1") && selectCashier && StrUtil.isNotBlank(thisCnInoid) && StrUtil.isBlank(accvoucher.getCcashier())) { // ???????????? ????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "6"));
                    } else if (scopeCondition.equals("0") && selectMakerNo && null != accvoucher.getCdirector() && !accvoucher.getCdirector().equals(reviewMan)) { //??????????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "1"));
                    } /*else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("1")) {
                                errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "2"));
                            }*/ else if (scopeCondition.equals("1") && DateUtil.compare(DateUtil.parse(accvoucher.getDbillDate()), DateUtil.parse(reviewDate)) > 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "3"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("3")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "4"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("2")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "5"));
                    } else if (scopeCondition.equals("1") && kemus.size() == 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "8"));
                    } else if (scopeCondition.equals("1") && ((kemus.get(0).getBdept().equals("1") && StrUtil.isBlank(accvoucher.getCdeptId())) || (kemus.get(0).getBperson().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId())) || (kemus.get(0).getBcus().equals("1") && StrUtil.isBlank(accvoucher.getCcusId())) || (kemus.get(0).getBsup().equals("1") && StrUtil.isBlank(accvoucher.getCsupId()) || (kemus.get(0).getBitem().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId()))))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "9"));
                    } else if ((scopeCondition.equals("1") && StrUtil.isNotBlank(accvoucher.getCdirector())) || (scopeCondition.equals("0") && StrUtil.isBlank(accvoucher.getCdirector()))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", scopeCondition.equals("1") ? "10" : "11"));
                    } else { // ??????
                        passList.add(accvoucher);
                    }
                }
                // ??????????????????
                Set<String> keysets = new HashSet<>();
                for (String thisInfo : new HashSet<>(passList.stream().map(item -> item.getDbillDate().substring(0, 7) + "==" + item.getInoId()).collect(Collectors.toList()))) {
                    double mdSum = 0;
                    double mcSum = 0;
                    Accvoucher thisVoucher = null;
                    String yearMonth = thisInfo.split("==")[0];
                    String thisInoid = thisInfo.split("==")[1];
                    for (Accvoucher accvoucher : passList) {
                        if (ObjectUtil.equal(accvoucher.getDbillDate().substring(0, 7), yearMonth) && ObjectUtil.equal(accvoucher.getInoId(), thisInoid)) {
                            mdSum += StrUtil.isBlank(accvoucher.getMd()) ? 0 : new BigDecimal(accvoucher.getMd()).doubleValue();
                            mcSum += StrUtil.isBlank(accvoucher.getMc()) ? 0 : new BigDecimal(accvoucher.getMc()).doubleValue();
                            if (null == thisVoucher) thisVoucher = accvoucher;
                        }
                    }
                    if (null != thisVoucher && thisInoid.equals(thisVoucher.getInoId())) {
                        if (!NumberUtil.equals(new BigDecimal(mdSum).setScale(2, BigDecimal.ROUND_HALF_UP), new BigDecimal(mcSum).setScale(2, BigDecimal.ROUND_HALF_UP))) {
                            errorList.add(CollectOfUtils.mapof("dbillDate", thisVoucher.getDbillDate(), "inoId", thisInoid, "errorType", "7"));
                        } else {
                            keysets.add(thisVoucher.getUniqueCode());
                        }
                    }
                }
                if (errorList.size() > 0) errorList = removeDuplicateEntries(errorList);
                reusltVo.setSuccessNumber(keysets.size());
                reusltVo.setErrorNumber(errorList.size() > 0 ? new HashSet<>(errorList.stream().map(item -> item.get("dbillDate") + "==" + item.get("inoId")).collect(Collectors.toList())).size() : 0);
                reusltVo.setErrorList(errorList);
                if (keysets.size() == 0)
                    return (scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByCdirectorByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByCdirectorByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0)).flatMap(num -> {
                        reusltVo.setSuccessNumber(0);
                        reusltVo.setPassNumber(num);
                        return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????" + taskName).thenReturn(R.ok(reusltVo));
                    });
                return (scopeCondition.equals("1") ? accvoucherRepository.symbolVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets, reviewMan) : accvoucherRepository.closeSymbolVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets)).collectList().flatMap(ressults -> {
                    reusltVo.setSuccessNumber(keysets.size());
                    return scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByCdirectorByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByCdirectorByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0);
                }).flatMap(total -> {
                            reusltVo.setPassNumber(total);
                            return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????" + taskName).thenReturn(R.ok(reusltVo));
                        }

                );
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("symbolPingZhengOld")
    @Transactional
    public Mono<R> symbolPingZhengOld(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Boolean selectCashier = Boolean.valueOf(map.get("selectCashier").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            ;
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodeAndConditionSign(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionSign(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionSign(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherSignFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
                       /* if (list.size() == 0 || !selectCashier)return Mono.just(resultMap);
                        return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList()
                                .map(codeList->{
                                    resultMap.put("codeList",codeList);
                                    return resultMap;
                                });*/
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0??????????????????"));
                }
                // ????????????????????????????????????????????????????????????
                     /*   if (selectCashier){
                            List<CodeKemu>  codeList = (List<CodeKemu>) resultMap.get("codeList");
                            if (codeList.size() > 0){
                                for (Accvoucher accvoucher : voucherList) {
                                    for (CodeKemu codeKemu : codeList) {
                                        if (accvoucher.getCcode().equals(codeKemu.getCcode())){
                                            ERROR_INFO = "???????????????????????????????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"?????????????????????" +
                                                    "???????????????????????????????????????????????????????????????";
                                            break;
                                        }
                                    }
                                }
                            }
                        }*/
                for (Accvoucher accvoucher : voucherList) {
                    if (StrUtil.isBlank(accvoucher.getCcheck())) {
                        ERROR_INFO = "???????????????????????????????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "???????????????????????????" + "???????????????????????????????????????????????????????????????";
                        break;
                    }
                }
                if (/*selectCashier &&*/ StrUtil.isNotBlank(ERROR_INFO))
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));
                // ????????????
                Set<String> keys = new HashSet<>();
                //String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+keys.size()+"??????????????????")));*/
                return accvoucherRepository.symbolVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys, reviewMan).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "??????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("closesymbolPingZheng")
    @Transactional
    public Mono<R> closesymbolPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Boolean selectCashier = Boolean.valueOf(map.get("selectCashier").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            ;
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodeAndConditionSignClose(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionSignClose(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionSignClose(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherSignFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
                       /* if (list.size() == 0 || !selectCashier)return Mono.just(resultMap);
                        return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList()
                                .map(codeList->{
                                    resultMap.put("codeList",codeList);
                                    return resultMap;
                                });*/
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0????????????????????????"));
                }
                // ????????????????????????????????????????????????????????????
                     /*   if (selectCashier){
                            List<CodeKemu>  codeList = (List<CodeKemu>) resultMap.get("codeList");
                            if (codeList.size() > 0){
                                for (Accvoucher accvoucher : voucherList) {
                                    for (CodeKemu codeKemu : codeList) {
                                        if (accvoucher.getCcode().equals(codeKemu.getCcode())){
                                            ERROR_INFO = "???????????????????????????????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"?????????????????????" +
                                                    "???????????????????????????????????????????????????????????????";
                                            break;
                                        }
                                    }
                                }
                            }
                        }*/
                       /* for (Accvoucher accvoucher : voucherList) {
                            if (StrUtil.isBlank(accvoucher.getCcheck())){
                                ERROR_INFO = "???????????????????????????????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"???????????????????????????" +
                                        "?????????????????????????????????????????????????????????????????????";
                                break;
                            }
                        }
                        if (/*selectCashier && StrUtil.isNotBlank(ERROR_INFO))return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));*/
                // ????????????
                Set<String> keys = new HashSet<>();
                //String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+keys.size()+"??????????????????")));*/
                return accvoucherRepository.closeSymbolVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "????????????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }


    @PostMapping("bookPingZheng")
    @Transactional
    public Mono<R> bookPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            String taskName = scopeCondition.equals("1") ? "??????" : "??????";

            String reviewMan = map.get("operatorUserName").toString();
            VoucherBusCheckVo reusltVo = new VoucherBusCheckVo();
            List<String> keys = (List<String>) map.get("selectedRowKeys");
            List<String> interval = ListUtil.toList(map.get("thisInterval").toString().split(" ~ "));
            reusltVo.setSelectNumber(keys.size());
            Map<String, Boolean> financialParameters = (Map<String, Boolean>) map.get("financialParameters");
            Boolean selectCashier = financialParameters.get("icashier"); // ????????????????????????
            Boolean selectImanager = financialParameters.get("imanager"); // ????????????????????????
            Boolean selectifVerify = financialParameters.get("ifVerify"); // ??????????????????
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodeAndConditionBook(keys).collectList();
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("??????" + taskName).setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(taskName);
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                // ????????????????????????
                Mono<List<CodeKemu>> cashiersMono = codeKemuRepository.findAllByYearAndCashierConditions(interval.get(0).substring(0, 4)).collectList();
                // ????????????????????????
                Mono<List<CodeKemu>> lastsMono = codeKemuRepository.findAllByBendAndIyearOrderByCcode("1", interval.get(0).substring(0, 4)).collectList();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0 || !selectCashier) return lastsMono.flatMap(lastList -> {
                    resultMap.put("lastCodeList", lastList);
                    return Mono.just(resultMap);
                });
                return Mono.zip(cashiersMono, lastsMono).flatMap(codeList -> {
                    resultMap.put("cashierCodeList", codeList.getT1());
                    resultMap.put("lastCodeList", codeList.getT2());
                    return Mono.just(resultMap);
                });
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                // ????????????????????????????????????????????????????????????
                List<CodeKemu> cashierCodeList = (List<CodeKemu>) resultMap.get("cashierCodeList");
                List<CodeKemu> lastCodeList = (List<CodeKemu>) resultMap.get("lastCodeList");
                List<Map<String, String>> errorList = new ArrayList<>();
                List<Accvoucher> passList = new ArrayList<>();
                String reviewDate = DateUtil.today();
                String thisCnInoid = "";
                for (Accvoucher accvoucher : voucherList) {
                    List<CodeKemu> kemus = lastCodeList.stream().filter(item -> item.getCcode().equals(accvoucher.getCcode())).collect(Collectors.toList());
                    if (selectCashier && cashierCodeList.size() > 0) {
                        if (StrUtil.isBlank(thisCnInoid) || !thisCnInoid.equals(accvoucher.getInoId())) {
                            thisCnInoid = checkVoucherIsCashierVoucher(voucherList, cashierCodeList, accvoucher);
                        }
                    }
                    if (scopeCondition.equals("1") && selectCashier && StrUtil.isNotBlank(thisCnInoid) && StrUtil.isBlank(accvoucher.getCcashier())) { // ???????????? ????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "6"));
                    } /*else if (scopeCondition.equals("1") && !selectMaker && accvoucher.getCbill().equals(reviewMan)) { // ?????????????????????????????????
                                errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "1"));
                            } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("1")) {
                                errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "2"));
                            }*/ else if (scopeCondition.equals("1") && DateUtil.compare(DateUtil.parse(accvoucher.getDbillDate()), DateUtil.parse(reviewDate)) > 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "3"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("3")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "4"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("2")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "5"));
                    } else if (scopeCondition.equals("1") && kemus.size() == 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "8"));
                    } else if (scopeCondition.equals("1") && ((kemus.get(0).getBdept().equals("1") && StrUtil.isBlank(accvoucher.getCdeptId())) || (kemus.get(0).getBperson().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId())) || (kemus.get(0).getBcus().equals("1") && StrUtil.isBlank(accvoucher.getCcusId())) || (kemus.get(0).getBsup().equals("1") && StrUtil.isBlank(accvoucher.getCsupId()) || (kemus.get(0).getBitem().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId()))))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "9"));
                    } else if ((scopeCondition.equals("1") && StrUtil.isNotBlank(accvoucher.getIbook())) || (scopeCondition.equals("0") && StrUtil.isBlank(accvoucher.getIbook()))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", scopeCondition.equals("1") ? "10" : "11"));
                    } else if (scopeCondition.equals("1") && selectifVerify && StrUtil.isBlank(accvoucher.getCcheck())) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "12"));
                    } else if (scopeCondition.equals("1") && selectImanager && StrUtil.isBlank(accvoucher.getCdirector())) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "13"));
                    } else { // ??????
                        passList.add(accvoucher);
                    }
                }
                // ??????????????????
                Set<String> keysets = new HashSet<>();
                for (String thisInfo : new HashSet<>(passList.stream().map(item -> item.getDbillDate().substring(0, 7) + "==" + item.getInoId()).collect(Collectors.toList()))) {
                    double mdSum = 0;
                    double mcSum = 0;
                    Accvoucher thisVoucher = null;
                    String yearMonth = thisInfo.split("==")[0];
                    String thisInoid = thisInfo.split("==")[1];
                    for (Accvoucher accvoucher : passList) {
                        if (ObjectUtil.equal(accvoucher.getDbillDate().substring(0, 7), yearMonth) && ObjectUtil.equal(accvoucher.getInoId(), thisInoid)) {
                            mdSum += StrUtil.isBlank(accvoucher.getMd()) ? 0 : new BigDecimal(accvoucher.getMd()).doubleValue();
                            mcSum += StrUtil.isBlank(accvoucher.getMc()) ? 0 : new BigDecimal(accvoucher.getMc()).doubleValue();
                            if (null == thisVoucher) thisVoucher = accvoucher;
                        }
                    }
                    if (null != thisVoucher && thisInoid.equals(thisVoucher.getInoId())) {
                        if (!NumberUtil.equals(new BigDecimal(mdSum).setScale(2, BigDecimal.ROUND_HALF_UP), new BigDecimal(mcSum).setScale(2, BigDecimal.ROUND_HALF_UP))) {
                            errorList.add(CollectOfUtils.mapof("dbillDate", thisVoucher.getDbillDate(), "inoId", thisInoid, "errorType", "7"));
                        } else {
                            keysets.add(thisVoucher.getUniqueCode());
                        }
                    }
                }
                if (errorList.size() > 0) errorList = removeDuplicateEntries(errorList);
                reusltVo.setSuccessNumber(keysets.size());
                reusltVo.setErrorNumber(errorList.size() > 0 ? new HashSet<>(errorList.stream().map(item -> item.get("dbillDate") + "==" + item.get("inoId")).collect(Collectors.toList())).size() : 0);
                reusltVo.setErrorList(errorList);
                if (keysets.size() == 0)
                    return (scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByIbookByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByIbookByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0)).flatMap(num -> {
                        reusltVo.setSuccessNumber(0);
                        reusltVo.setPassNumber(num);
                        return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????" + taskName).thenReturn(R.ok(reusltVo));
                    });
                return (scopeCondition.equals("1") ? accvoucherRepository.bookVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets, reviewMan, reviewDate) : accvoucherRepository.closeBookVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets)).collectList().flatMap(ressults -> {
                    reusltVo.setSuccessNumber(keysets.size());
                    return scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByIbookByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByIbookByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0);
                }).flatMap(total -> {
                            reusltVo.setPassNumber(total);
                            return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????" + taskName).thenReturn(R.ok(reusltVo));
                        }

                );
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(new VoucherBusCheckVo())));
    }

    @PostMapping("bookPingZhengOld")
    @Transactional
    public Mono<R> bookPingZhengOld(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Boolean selectImanager = Boolean.valueOf(map.get("selectImanager").toString());
            Boolean selectCashier = Boolean.valueOf(map.get("selectCashier").toString());
            Boolean selectifVerify = Boolean.valueOf(map.get("selectifVerify").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            ;
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodeAndConditionBook(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionBook(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionBook(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherSignFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0 || !selectCashier) return Mono.just(resultMap);
                return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList().map(codeList -> {
                    resultMap.put("codeList", codeList);
                    return resultMap;
                });
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0??????????????????"));
                }
                // ????????????????????????????????????????????????????????????
                if (selectCashier) {
                    List<CodeKemu> codeList = (List<CodeKemu>) resultMap.get("codeList");
                    if (codeList.size() > 0) {
                        for (Accvoucher accvoucher : voucherList) {
                            for (CodeKemu codeKemu : codeList) {
                                if (accvoucher.getCcode().equals(codeKemu.getCcode())) {
                                    ERROR_INFO = "???????????????????????????????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "?????????????????????" + "???????????????????????????????????????????????????????????????";
                                    break;
                                }
                            }
                        }
                    }
                }
                if (StrUtil.isBlank(ERROR_INFO) && selectifVerify) {
                    for (Accvoucher accvoucher : voucherList) {
                        if (StrUtil.isBlank(accvoucher.getCcheck())) {
                            ERROR_INFO = "????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";
                            break;
                        }
                    }
                }
                if (StrUtil.isBlank(ERROR_INFO) && selectImanager) {
                    for (Accvoucher accvoucher : voucherList) {
                        if (StrUtil.isBlank(accvoucher.getCdirector())) {
                            ERROR_INFO = "????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";
                            break;
                        }
                    }
                }
                if (StrUtil.isNotBlank(ERROR_INFO))
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));
                // ????????????
                Set<String> keys = new HashSet<>();
                String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+keys.size()+"??????????????????")));*/
                return accvoucherRepository.bookVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys, reviewMan, reviewDate).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "??????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("closeBookPingZheng")
    @Transactional
    public Mono<R> closeBookPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Boolean selectImanager = Boolean.valueOf(map.get("selectImanager").toString());
            Boolean selectCashier = Boolean.valueOf(map.get("selectCashier").toString());
            Boolean selectifVerify = Boolean.valueOf(map.get("selectifVerify").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            ;
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodeAndConditionBookClose(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionBookClose(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionBookClose(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherSignFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
                       /* if (list.size() == 0 || !selectCashier)return Mono.just(resultMap);
                        return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList()
                                .map(codeList->{
                                    resultMap.put("codeList",codeList);
                                    return resultMap;
                                });*/
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0????????????????????????"));
                }
                // ????????????????????????????????????????????????????????????
                    /*    if (selectCashier){
                            List<CodeKemu>  codeList = (List<CodeKemu>) resultMap.get("codeList");
                            if (codeList.size() > 0){
                                for (Accvoucher accvoucher : voucherList) {
                                    for (CodeKemu codeKemu : codeList) {
                                        if (accvoucher.getCcode().equals(codeKemu.getCcode())){
                                            ERROR_INFO = "???????????????????????????????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"?????????????????????" +
                                                    "???????????????????????????????????????????????????????????????";
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (StrUtil.isBlank(ERROR_INFO) &&  selectifVerify){
                            for (Accvoucher accvoucher : voucherList) {
                                if (StrUtil.isBlank(accvoucher.getCcheck())){
                                    ERROR_INFO = "????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";
                                    break;
                                }
                            }
                        }
                        if (StrUtil.isBlank(ERROR_INFO) &&  selectImanager){
                            for (Accvoucher accvoucher : voucherList) {
                                if (StrUtil.isBlank(accvoucher.getCdirector())){
                                    ERROR_INFO = "????????????????????????????????????????????????"+accvoucher.getDbillDate()+"????????????:???"+accvoucher.getInoId()+"??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";
                                    break;
                                }
                            }
                        }
                        if (StrUtil.isNotBlank(ERROR_INFO))return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));*/
                // ????????????
                Set<String> keys = new HashSet<>();
                //String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+keys.size()+"??????????????????")));*/
                return accvoucherRepository.closeBookVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "????????????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("changeStatusPingZheng")
    @Transactional
    public Mono<R> changeStatusPingZheng(@RequestBody Map map) {
        String scopeCondition = map.get("setValue").toString();
        String taskName = (scopeCondition.equals("1") ? "??????" : "??????");
        try {
            String reviewMan = map.get("operatorUserName").toString();
            List<String> keysList = (List<String>) map.get("selectedRowKeys");
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodes(keysList).collectList();

            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule(taskName + "??????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(taskName);
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
            }).flatMap(resultMap -> {
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0???????????????" + taskName + "???"));
                }
                for (Accvoucher accvoucher : voucherList) {
                    if (StrUtil.isNotBlank(accvoucher.getCcheck())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCcashier())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCdirector())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "??????????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCbook())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (Objects.equals(accvoucher.getIfrag(), "1") || Objects.equals(accvoucher.getIfrag(), "3")) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "??????" + (accvoucher.getIfrag().equals("1") ? "??????" : "??????") + "???????????????????????????????????????" + (accvoucher.getIfrag().equals("1") ? "??????" : "??????") + "????????????????????????" + taskName + "???????????????";
                        break;
                    }
                }

                if (StrUtil.isNotBlank(ERROR_INFO))
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(R.ok(ERROR_INFO));
                // ????????????
                Set<String> keys = new HashSet<>();
                String reviewDate = DateUtil.now();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                Flux<Accvoucher> result = null;
                if (scopeCondition.equals("1")) {
                    result = accvoucherRepository.changeVoucherStatusByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys, reviewMan, reviewDate, scopeCondition);
                } else {
                    result = accvoucherRepository.changeVoucherStatusByYaerAndUniqueCodes2(voucherList.get(0).getIyear(), keys, reviewMan, reviewDate, scopeCondition);
                }
                return result.collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(R.ok("?????????" + keys.size() + "?????????" + taskName + "???")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(o));
    }

    @PostMapping("resetStatusPingZheng")
    @Transactional
    public Mono<R> resetStatusPingZheng(@RequestBody Map map) {
        String scopeCondition = map.get("setValue").toString();
        String taskName = (scopeCondition.equals("1") ? "????????????" : "????????????");
        try {
            String reviewMan = map.get("operatorUserName").toString();
            List<String> keysList = (List<String>) map.get("selectedRowKeys");
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodesClose(keysList, scopeCondition).collectList();
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule(taskName + "??????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(taskName);
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
            }).flatMap(resultMap -> {
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0?????????" + taskName + "???"));
                }
                String ERROR_INFO = "";
                for (Accvoucher accvoucher : voucherList) {
                    if (StrUtil.isNotBlank(accvoucher.getCcashier())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCcheck())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCdirector())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "??????????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCbook())) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????" + taskName + "???????????????";
                        break;
                    }
                    if ((scopeCondition.equals("1") && Objects.equals(accvoucher.getIfrag(), "3")) || (scopeCondition.equals("3") && Objects.equals(accvoucher.getIfrag(), "1"))) {
                        ERROR_INFO = "??????" + taskName + "??????!???????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "??????" + (accvoucher.getIfrag().equals("1") ? "??????" : "??????") + "???????????????????????????????????????" + (accvoucher.getIfrag().equals("1") ? "??????" : "??????") + "????????????????????????" + taskName + "???????????????";
                        break;
                    }
                }
                if (StrUtil.isNotBlank(ERROR_INFO))
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(R.ok(ERROR_INFO));
                // ????????????
                Set<String> keys = new HashSet<>();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                return accvoucherRepository.resetVoucherStatusByYearAndUniqueCodes(voucherList.get(0).getIyear(), keys, scopeCondition).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(R.ok("?????????" + keys.size() + "?????????" + taskName + "???")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", taskName + "??????").thenReturn(o));
    }


    @PostMapping("cashierPingZheng")
    @Transactional
    public Mono<R> cashierPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            String taskName = scopeCondition.equals("1") ? "????????????" : "????????????";
            String reviewMan = map.get("operatorUserName").toString();
            VoucherBusCheckVo reusltVo = new VoucherBusCheckVo();
            List<String> keys = (List<String>) map.get("selectedRowKeys");
            List<String> interval = ListUtil.toList(map.get("thisInterval").toString().split(" ~ "));
            Map<String, Boolean> financialParameters = (Map<String, Boolean>) map.get("financialParameters");
            Boolean selectMakerNo = financialParameters.get("icashierNo"); // ?????????????????????
            reusltVo.setSelectNumber(keys.size());
            Mono<List<Accvoucher>> queryList = accvoucherRepository.findAllByUniqueCodes(keys).collectList();
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("??????" + taskName).setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod(taskName);
                return taskRepository.save(task).map(o -> list);
            }).flatMap(list -> {
                // ????????????????????????
                Mono<List<CodeKemu>> cashiersMono = codeKemuRepository.findAllByYearAndCashierConditions(interval.get(0).substring(0, 4)).collectList();
                // ????????????????????????
                Mono<List<CodeKemu>> lastsMono = codeKemuRepository.findAllByBendAndIyearOrderByCcode("1", interval.get(0).substring(0, 4)).collectList();
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0) return lastsMono.flatMap(lastList -> {
                    resultMap.put("lastCodeList", lastList);
                    return Mono.just(resultMap);
                });
                return Mono.zip(cashiersMono, lastsMono).flatMap(codeList -> {
                    resultMap.put("cashierCodeList", codeList.getT1());
                    resultMap.put("lastCodeList", codeList.getT2());
                    return Mono.just(resultMap);
                });
            }).flatMap(resultMap -> {
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                // ????????????????????????????????????????????????????????????
                List<CodeKemu> cashierCodeList = (List<CodeKemu>) resultMap.get("cashierCodeList");
                List<CodeKemu> lastCodeList = (List<CodeKemu>) resultMap.get("lastCodeList");
                List<Map<String, String>> errorList = new ArrayList<>();
                List<Accvoucher> passList = new ArrayList<>();
                String reviewDate = DateUtil.today();
                String thisCnInoid = "";
                for (Accvoucher accvoucher : voucherList) {
                    List<CodeKemu> kemus = lastCodeList.stream().filter(item -> item.getCcode().equals(accvoucher.getCcode())).collect(Collectors.toList());
                    if (cashierCodeList.size() > 0) {
                        if (StrUtil.isBlank(thisCnInoid) || !thisCnInoid.equals(accvoucher.getInoId())) {
                            thisCnInoid = checkVoucherIsCashierVoucher(voucherList, cashierCodeList, accvoucher);
                        }
                    }
                    if (StrUtil.isBlank(thisCnInoid)) { // ??????????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "6"));
                    } else if (scopeCondition.equals("0") && selectMakerNo && null != accvoucher.getCcashier() && !accvoucher.getCcashier().equals(reviewMan)) { // ?????????????????????
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "1"));
                    } /*else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("1")) {
                                errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "2"));
                            }*/ else if (scopeCondition.equals("1") && DateUtil.compare(DateUtil.parse(accvoucher.getDbillDate()), DateUtil.parse(reviewDate)) > 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "3"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("3")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "4"));
                    } else if (scopeCondition.equals("1") && accvoucher.getIfrag().equals("2")) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "5"));
                    } else if (scopeCondition.equals("1") && kemus.size() == 0) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "8"));
                    } else if (scopeCondition.equals("1") && ((kemus.get(0).getBdept().equals("1") && StrUtil.isBlank(accvoucher.getCdeptId())) || (kemus.get(0).getBperson().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId())) || (kemus.get(0).getBcus().equals("1") && StrUtil.isBlank(accvoucher.getCcusId())) || (kemus.get(0).getBsup().equals("1") && StrUtil.isBlank(accvoucher.getCsupId()) || (kemus.get(0).getBitem().equals("1") && StrUtil.isBlank(accvoucher.getCpersonId()))))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", "9"));
                    } else if ((scopeCondition.equals("1") && StrUtil.isNotBlank(accvoucher.getCcashier())) || (scopeCondition.equals("0") && StrUtil.isBlank(accvoucher.getCcashier()))) {
                        errorList.add(CollectOfUtils.mapof("dbillDate", accvoucher.getDbillDate(), "inoId", accvoucher.getInoId(), "errorType", scopeCondition.equals("1") ? "10" : "11"));
                    } else { // ??????
                        passList.add(accvoucher);
                    }
                    if (StrUtil.isNotBlank(thisCnInoid)) thisCnInoid = accvoucher.getInoId();
                }
                // ??????????????????
                Set<String> keysets = new HashSet<>();
                for (String thisInfo : new HashSet<>(passList.stream().map(item -> item.getDbillDate().substring(0, 7) + "==" + item.getInoId()).collect(Collectors.toList()))) {
                    double mdSum = 0;
                    double mcSum = 0;
                    Accvoucher thisVoucher = null;
                    String yearMonth = thisInfo.split("==")[0];
                    String thisInoid = thisInfo.split("==")[1];
                    for (Accvoucher accvoucher : passList) {
                        if (ObjectUtil.equal(accvoucher.getDbillDate().substring(0, 7), yearMonth) && ObjectUtil.equal(accvoucher.getInoId(), thisInoid)) {
                            mdSum += StrUtil.isBlank(accvoucher.getMd()) ? 0 : new BigDecimal(accvoucher.getMd()).doubleValue();
                            mcSum += StrUtil.isBlank(accvoucher.getMc()) ? 0 : new BigDecimal(accvoucher.getMc()).doubleValue();
                            if (null == thisVoucher) thisVoucher = accvoucher;
                        }
                    }
                    if (null != thisVoucher && thisInoid.equals(thisVoucher.getInoId())) {
                        if (!NumberUtil.equals(new BigDecimal(mdSum).setScale(2, BigDecimal.ROUND_HALF_UP), new BigDecimal(mcSum).setScale(2, BigDecimal.ROUND_HALF_UP))) {
                            errorList.add(CollectOfUtils.mapof("dbillDate", thisVoucher.getDbillDate(), "inoId", thisInoid, "errorType", "7"));
                        } else {
                            keysets.add(thisVoucher.getUniqueCode());
                        }
                    }
                }
                if (errorList.size() > 0) errorList = removeDuplicateEntries(errorList);
                reusltVo.setSuccessNumber(keysets.size());
                reusltVo.setErrorNumber(errorList.size() > 0 ? new HashSet<>(errorList.stream().map(item -> item.get("dbillDate") + "==" + item.get("inoId")).collect(Collectors.toList())).size() : 0);
                reusltVo.setErrorList(errorList);
                if (keysets.size() == 0)
                    return (scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByCcashierByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByCcashierByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0)).flatMap(num -> {
                        reusltVo.setSuccessNumber(0);
                        reusltVo.setPassNumber(num);
                        return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????" + taskName).thenReturn(R.ok(reusltVo));
                    });
                return (scopeCondition.equals("1") ? accvoucherRepository.cashierVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets, reviewMan, reviewDate) : accvoucherRepository.closeCashierVoucherByYaerAndUniqueCodes(passList.get(0).getIyear(), keysets)).collectList().flatMap(ressults -> {
                    reusltVo.setSuccessNumber(keysets.size());
                    return scopeCondition.equals("1") ? interval.get(0).length() > 7 ? accvoucherRepository.countByCcashierByDate(interval.get(0), interval.get(1)) : accvoucherRepository.countByCcashierByPeriod(interval.get(0).replace("-", ""), interval.get(1).replace("-", "")) : Mono.just(0);
                }).flatMap(total -> {
                            reusltVo.setPassNumber(total);
                            return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????" + taskName).thenReturn(R.ok(reusltVo));
                        }

                );
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    /**
     * ???????????????????????????????????????
     */
    private String checkVoucherIsCashierVoucher(List<Accvoucher> voucherList, List<CodeKemu> cashierCodeList, Accvoucher thisVoucher) {
        String resutl = "";
        Set<String> voucherCodeList = new HashSet<>(voucherList.stream().filter(item -> item.getDbillDate().substring(0, 7).equals(thisVoucher.getDbillDate().substring(0, 7)) && item.getInoId().equals(thisVoucher.getInoId())).map(item -> item.getCcode()).collect(Collectors.toList()));
        for (CodeKemu codeKemu : cashierCodeList) {
            if (voucherCodeList.contains(codeKemu.getCcode())) {
                resutl = thisVoucher.getInoId();
                break;
            }
        }
        return resutl;
    }


    @PostMapping("cashierPingZhengOld")
    @Transactional
    public Mono<R> cashierPingZhengOld(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            ;
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodes(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionCashier(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionCashier(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherSignFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                if (list.size() == 0) return Mono.just(resultMap);
                return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList().map(codeList -> {
                    resultMap.put("codeList", codeList);
                    return resultMap;
                });
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                List<CodeKemu> codeList = (List<CodeKemu>) resultMap.get("codeList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????0????????????????????????"));
                }
                for (Accvoucher accvoucher : voucherList) {
                    if (accvoucher.getIbook().equals("1")) {
                        ERROR_INFO = "?????????????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";
                        break;
                    }
                    if (StrUtil.isNotBlank(accvoucher.getCdirector())) {
                        ERROR_INFO = "?????????????????????????????????????????????????????????" + accvoucher.getDbillDate() + "????????????:???" + accvoucher.getInoId() + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????";
                        break;
                    }
                }
                if (StrUtil.isNotBlank(ERROR_INFO))
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok(ERROR_INFO));
                // ????????????
                Set<String> keys = new HashSet<>();
                String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    for (CodeKemu codeKemu : codeList) {
                        if (accvoucher.getCcode().equals(codeKemu.getCcode())) {
                            keys.add(accvoucher.getUniqueCode());
                            break;
                        }
                    }
                }
                return accvoucherRepository.cashierVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys, reviewMan, reviewDate).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(R.ok("?????????" + keys.size() + "????????????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("closeCashierPingZheng")
    @Transactional
    public Mono<R> closeCashierPingZheng(@RequestBody Map map) {
        try {
            String scopeCondition = map.get("scopeCondition").toString();
            Boolean selectMaker = Boolean.valueOf(map.get("selectMaker").toString());
            Map<String, String> batchCondition = (HashMap<String, String>) map.get("batchCondition");
            Mono<List<Accvoucher>> queryList = null;
            String reviewMan = map.get("operatorUserName").toString();
            ;
            if (scopeCondition.equals("1")) {
                List<String> keys = (List<String>) map.get("selectedRowKeys");
                queryList = accvoucherRepository.findAllByUniqueCodes(keys).collectList();
            } else {
                if (StrUtil.isNotBlank(batchCondition.get("voucherPeriod"))) {
                    queryList = accvoucherRepository.findAllByPeriodAndConditionCashierClose(batchCondition.get("voucherPeriod").replaceAll("-", "")).collectList();
                } else {
                    queryList = accvoucherRepository.findAllByIntervalAndConditionCashierClose(batchCondition.get("voucherDateStart"), batchCondition.get("voucherDateEnd")).collectList();
                }
            }
            return queryList.flatMap(list -> {
                Task task = new Task().setCaozuoUnique(reviewMan).setFunctionModule("??????????????????").setTime(DateUtil.now()).setState("1").setIyear(DateUtil.thisYear() + "").setMethod("??????");
                return taskRepository.save(task).map(o -> list);
            }).map(list -> voucherSignFilter(list, batchCondition, selectMaker, reviewMan)).flatMap(list -> {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("voucherList", list);
                return Mono.just(resultMap);
                       /* if (list.size() == 0 || !selectCashier)return Mono.just(resultMap);
                        return codeKemuRepository.findAllByYearAndCashierConditions(list.get(0).getIyear()).collectList()
                                .map(codeList->{
                                    resultMap.put("codeList",codeList);
                                    return resultMap;
                                });*/
            }).flatMap(resultMap -> {
                Map<String, Object> a = resultMap;
                String ERROR_INFO = "";
                List<Accvoucher> voucherList = (List<Accvoucher>) resultMap.get("voucherList");
                if (voucherList.size() == 0) {
                    return taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????????????????").thenReturn(R.ok("?????????0??????????????????????????????"));
                }
                // ????????????????????????????????????????????????????????????
                // ????????????
                Set<String> keys = new HashSet<>();
                //String reviewDate = DateUtil.today();
                for (Accvoucher accvoucher : voucherList) {
                    keys.add(accvoucher.getUniqueCode());
                }
                       /*return Mono.just(new ArrayList<>()).flatMap(ressults->
                               taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????")
                                       .thenReturn(R.ok("?????????"+keys.size()+"??????????????????")));*/
                return accvoucherRepository.closeCashierVoucherByYaerAndUniqueCodes(voucherList.get(0).getIyear(), keys).collectList().flatMap(ressults -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "??????????????????").thenReturn(R.ok("?????????" + keys.size() + "??????????????????????????????")));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Mono.just(R.error()).flatMap(o -> taskRepository.deleteByIyearAndFunctionModule(DateUtil.thisYear() + "", "????????????").thenReturn(o));
    }

    @PostMapping("changeSettlementInfo")
    @Transactional
    public Mono<R> changeSettlementInfo(@RequestBody Accvoucher accvoucher) {
        return accvoucherRepository.findAllByVoucherByVouchUnCode(accvoucher.getIyear(), accvoucher.getUniqueCode(), accvoucher.getVouchUnCode()).collectList().flatMap(list -> {
            for (Accvoucher dbEntity : list) {
                dbEntity.setPjCsettle(accvoucher.getPjCsettle());
                dbEntity.setPjId(accvoucher.getPjId());
                dbEntity.setPjDate(accvoucher.getPjDate());
                dbEntity.setPjUnitName(accvoucher.getPjUnitName());
            }
            return accvoucherRepository.saveAll(list).collectList().map(R::ok);
        });
    }

    @PostMapping("changeXjllInfo")
    @Transactional
    public Mono<R> changeXjllInfo(@RequestBody Accvoucher accvoucher) {
        return accvoucherRepository.findAllByVoucherByVouchUnCodeXj(accvoucher.getIyear(), accvoucher.getUniqueCode(), accvoucher.getVouchUnCode()).collectList().flatMap(list -> {
            for (Accvoucher dbEntity : list) {
                dbEntity.setCashProject(accvoucher.getCashProject());
            }
            return accvoucherRepository.saveAll(list).collectList().map(R::ok);
        });
    }

    private List<Accvoucher> voucherReviewFilter(List<Accvoucher> list, Map<String, String> condition, Boolean selectMaker, String operationName) {
        String startV = condition.get("voucherNumberStart").trim();
        String endV = condition.get("voucherNumberEnd").trim();
        String maker = condition.get("maker").trim();
        String cashier = condition.get("cashier").trim();
        String checkName = condition.get("checkName").trim();
        return list.stream().filter(item -> {
            if (!selectMaker && item.getCbill().equals(operationName)) return false; // ????????????????????????
            if (StrUtil.isNotBlank(startV) && StrUtil.isNotBlank(endV)) {             // ????????????????????????
                if (item.getInoId().compareTo(startV) < 0 || item.getInoId().compareTo(endV) > 0) return false;
            }
            if ((StrUtil.isNotBlank(maker) && !item.getCcheck().equals(maker))) return false;
            if ((StrUtil.isNotBlank(cashier) && !item.getCcashier().equals(cashier))) return false;
            if ((StrUtil.isNotBlank(checkName) && !item.getCcheck().equals(checkName))) return false;
            return true;
        }).collect(Collectors.toList());
    }

    private List<Accvoucher> voucherSignFilter(List<Accvoucher> list, Map<String, String> condition, Boolean selectMaker, String operationName) {
        String startV = condition.get("voucherNumberStart").trim();
        String endV = condition.get("voucherNumberEnd").trim();
        String maker = condition.get("maker").trim();
        String cashier = condition.get("cashier").trim();
        String checkName = condition.get("checkName").trim();
        return list.stream().filter(item -> {
            if (StrUtil.isNotBlank(startV) && StrUtil.isNotBlank(endV)) {             // ????????????????????????
                if (item.getInoId().compareTo(startV) < 0 || item.getInoId().compareTo(endV) > 0) return false;
            }
            if ((StrUtil.isNotBlank(maker) && !item.getCcheck().equals(maker))) return false;
            if ((StrUtil.isNotBlank(cashier) && !item.getCcashier().equals(cashier))) return false;
            if ((StrUtil.isNotBlank(checkName) && !item.getCcheck().equals(checkName))) return false;
            return true;
        }).collect(Collectors.toList());
    }

    private List<Accvoucher> printFilter(List<Accvoucher> list, Map variableMap) {
        if (list.size() > 0) {
            String startV = variableMap.containsKey("voucherNumberStart") ? variableMap.get("voucherNumberStart").toString() : "";
            String endV = variableMap.containsKey("voucherNumberEnd") ? variableMap.get("voucherNumberEnd").toString() : "";
            String type = variableMap.containsKey("voucherType") ? variableMap.get("voucherType").toString() : "";
            String reviewStatus = variableMap.containsKey("reviewStatus") ? variableMap.get("reviewStatus").toString() : "";
            list = list.stream().filter(item -> {
                if (StrUtil.isNotBlank(startV) && StrUtil.isNotBlank(endV)) {
                    if ((Integer.valueOf(item.getInoId())).compareTo(Integer.valueOf(startV)) < 0 || (Integer.valueOf(item.getInoId()).compareTo((Integer.valueOf(endV))) > 0))
                        return false;
                }
                if (StrUtil.isNotBlank(type) && !type.equals(item.getCsign())) return false;
                if (StrUtil.isNotBlank(reviewStatus) && (StrUtil.isNotBlank(item.getCcheck()) ? !reviewStatus.equals(item.getCcheck()) : (reviewStatus.equals("1") || reviewStatus.equals("2"))))
                    return false;
                return true;
            }).collect(Collectors.toList());
        }
        return list;
    }

    private List<Accvoucher> queryFilter(List<Accvoucher> list, Map map) {
        if (list.size() > 0) {
            String queryMark = map.get("queryMark").toString();
            String queryType = map.get("biZhong").toString();
            Map<String, Object> variableMap = ((HashMap<String, HashMap<String, Object>>) map.get("condition")).get("variable");
            HashMap<String, Object> authorityMap = ((HashMap<String, HashMap<String, Object>>) map.get("condition")).get("authority");
            Map<String, String> searchMap = ((HashMap<String, String>) map.get("searchConditon"));
            Map<String, String> filterMap = ((HashMap<String, String>) map.get("filterConditon"));
            Map<String, Object> treeCondition = ((Map<String, Object>) map.get("treeConditon"));
            String startV = variableMap.containsKey("voucherNumberStart") ? variableMap.get("voucherNumberStart").toString() : "";
            String endV = variableMap.containsKey("voucherNumberEnd") ? variableMap.get("voucherNumberEnd").toString() : "";
            String type = variableMap.containsKey("voucherType") ? variableMap.get("voucherType").toString() : "";
            String summary = variableMap.containsKey("summary") ? variableMap.get("summary").toString() : "";
            String subjectNumber = variableMap.containsKey("subjectNumber") ? variableMap.get("subjectNumber").toString() : "";
            String amountMin = variableMap.containsKey("amountMin") ? variableMap.get("amountMin").toString() : "";
            String amountMax = variableMap.containsKey("amountMax") ? variableMap.get("amountMax").toString() : "";
            String direction = variableMap.containsKey("direction") ? variableMap.get("direction").toString() : "";

            String preparedMan = variableMap.containsKey("preparedMan") ? variableMap.get("preparedMan").toString() : "";
            String checkMan = variableMap.containsKey("checkMan") ? variableMap.get("checkMan").toString() : "";
            String cashierMan = variableMap.containsKey("cashierMan") ? variableMap.get("cashierMan").toString() : "";
            String bookMan = variableMap.containsKey("bookMan") ? variableMap.get("bookMan").toString() : "";
            String reviewMan = variableMap.containsKey("reviewMan") ? variableMap.get("reviewMan").toString() : "";

            String reviewStatus = variableMap.containsKey("reviewStatus") ? variableMap.get("reviewStatus").toString() : "";
            String bookStatus = variableMap.containsKey("bookStatus") ? variableMap.get("bookStatus").toString() : "";
            String ifrag = variableMap.containsKey("ifrag") ? variableMap.get("ifrag").toString() : "";
            Set<String> codes = new HashSet<>((List<String>) authorityMap.get("codes"));
            Set<String> types = new HashSet<>((List<String>) authorityMap.get("types"));

            // ???????????? ??????
            Map<String, List<String>> assistsMap = variableMap.containsKey("assists") ? (Map<String, List<String>>) variableMap.get("assists") : null;
            // ?????? ????????????
            list = list.stream().filter(item -> {
                if (StrUtil.isNotBlank(startV) && StrUtil.isNotBlank(endV)) {
                    if ((Integer.valueOf(item.getInoId())).compareTo(Integer.valueOf(startV)) < 0 || (Integer.valueOf(item.getInoId()).compareTo((Integer.valueOf(endV))) > 0))
                        return false;
                }
                if (StrUtil.isNotBlank(type) && !type.equals(item.getCsign())) return false;
                if (StrUtil.isNotBlank(summary) && !item.getCdigest().contains(summary)) return false;
                if (StrUtil.isNotBlank(bookStatus) && (StrUtil.isNotBlank(item.getIbook()) ? !bookStatus.equals(item.getIbook()) : (bookStatus.equals("1") || bookStatus.equals("2"))))
                    return false;
                if (StrUtil.isNotBlank(reviewStatus) && (StrUtil.isNotBlank(item.getCcheck()) ? !reviewStatus.equals(item.getCcheck()) : (reviewStatus.equals("1") || reviewStatus.equals("2"))))
                    return false;
                if (StrUtil.isNotBlank(ifrag) && !ifrag.equals(item.getIfrag())) return false;
                if (StrUtil.isNotBlank(subjectNumber) && !subjectNumber.equals(item.getCcode())) return false;

                if (StrUtil.isNotBlank(preparedMan) && !preparedMan.equals(item.getCbill())) return false;
                if (StrUtil.isNotBlank(checkMan) && !checkMan.equals(item.getCcheck())) return false;
                if (StrUtil.isNotBlank(cashierMan) && !cashierMan.equals(item.getCcashier())) return false;
                if (StrUtil.isNotBlank(bookMan) && !bookMan.equals(item.getCbook())) return false;
                if (StrUtil.isNotBlank(reviewMan) && !reviewMan.equals(item.getCdirector())) return false;
                if (StrUtil.isNotBlank(amountMin) && StrUtil.isNotBlank(amountMax)) {
                    BigDecimal md = StrUtil.isBlank(item.getMd()) ? new BigDecimal(0) : new BigDecimal(item.getMd());
                    BigDecimal mc = StrUtil.isBlank(item.getMc()) ? new BigDecimal(0) : new BigDecimal(item.getMc());
                    if (StrUtil.isNotBlank(direction) && direction.equals("jf")) {
                        if (md.compareTo(new BigDecimal(amountMin)) < 0 || md.compareTo(new BigDecimal(amountMax)) > 0)
                            return false;
                    } else if (StrUtil.isNotBlank(direction) && direction.equals("df")) {
                        if (mc.compareTo(new BigDecimal(amountMin)) < 0 || mc.compareTo(new BigDecimal(amountMax)) > 0)
                            return false;
                    } else if (StrUtil.isBlank(direction) && (md != new BigDecimal(0) || mc != new BigDecimal(0))) {
                        if (md != new BigDecimal(0)) {
                            if (md.compareTo(new BigDecimal(amountMin)) < 0 || md.compareTo(new BigDecimal(amountMax)) > 0)
                                return false;
                        } else if (mc != new BigDecimal(0)) {
                            if (mc.compareTo(new BigDecimal(amountMin)) < 0 || mc.compareTo(new BigDecimal(amountMax)) > 0)
                                return false;
                        }
                    }
                }
                if (queryMark.equals("1") && codes.size() > 0 && !(new HashSet<>(codes).contains(item.getCcode())))
                    return false;
                if (queryMark.equals("1") && types.size() > 0 && !(new HashSet<>(types).contains(item.getCsign())))
                    return false;

                // ????????????????????????
                if (null != assistsMap && assistsMap.keySet().size() > 0) {
                    for (String key : assistsMap.keySet()) {
                        Set<String> vals = new HashSet<>(assistsMap.get(key));
                        if (vals.size() > 0) {
                            String dbValue = getFieldValueByFieldName(key, item);
                            if (StrUtil.isBlank(dbValue) || !vals.contains(dbValue)) return false; // ?????? ??? ??????????????????
                        }
                    }
                }

                // ???????????????
                if (StrUtil.isNotBlank(filterMap.get("amountMin")) && StrUtil.isNotBlank(filterMap.get("amountMax"))) {
                    BigDecimal min = new BigDecimal(filterMap.get("amountMin"));
                    BigDecimal max = new BigDecimal(filterMap.get("amountMax"));
                    BigDecimal md = StrUtil.isBlank(item.getMd()) ? new BigDecimal(0) : new BigDecimal(item.getMd());
                    BigDecimal mc = StrUtil.isBlank(item.getMc()) ? new BigDecimal(0) : new BigDecimal(item.getMc());
                    if (md != new BigDecimal(0)) {
                        if (md.compareTo(min) < 0 || md.compareTo(max) > 0) return false;
                    } else if (mc != new BigDecimal(0)) {
                        if (mc.compareTo(min) < 0 || mc.compareTo(max) > 0) return false;
                    }
                }
                if (StrUtil.isNotBlank(filterMap.get("pzNumberMin")) && StrUtil.isNotBlank(filterMap.get("pzNumberMax"))) {
                    String pzNumberMin = Integer.valueOf(filterMap.get("pzNumberMin")) + "";
                    String pzNumberMax = Integer.valueOf(filterMap.get("pzNumberMax")) + "";
                    if (item.getInoId().compareTo(pzNumberMin) < 0 || item.getInoId().compareTo(pzNumberMax) > 0)
                        return false;
                }
                if (StrUtil.isNotBlank(filterMap.get("cashProject")) && StrUtil.isNotBlank(filterMap.get("cashProject"))) {
                    return filterMap.get("cashProject").equals(item.getCashProject());
                }

                // ????????????
                if (StrUtil.isNotBlank(searchMap.get("requirement")) && StrUtil.isNotBlank(searchMap.get("value"))) {
                    String value = searchMap.get("value");
                    if (searchMap.get("requirement").trim().equals("inoId")) {
                        if (!item.getCsign().contains(value) && !item.getInoId().contains(Integer.parseInt(value) + ""))
                            return false;
                    } else {
                        String dbValue = getFieldValueByFieldName(searchMap.get("requirement").trim(), item);
                        if (null != dbValue && !dbValue.contains(value)) return false;
                    }
                }
                // ??????????????????
                if (null != treeCondition && treeCondition.size() > 0) {
                    List<String> fList = (List<String>) treeCondition.get("list");
                    if (treeCondition.get("type").equals("month") && fList.size() > 0) {
                        if (!fList.contains(item.getDbillDate().substring(0, 7))) return false;
                    } else if (treeCondition.get("type").equals("day")) {
                        String monthValue = treeCondition.containsKey("monthVal") ? treeCondition.get("monthVal").toString() : "";
                        if (StrUtil.isNotBlank(monthValue) && !monthValue.equals(item.getDbillDate().substring(5, 7)))
                            return false;
                        if (fList.size() > 0 && !fList.contains(item.getDbillDate() + "-" + item.getInoId()))
                            return false;
                    }
                }
                // ?????? ?????????
                if (StrUtil.isNotBlank(queryType)) { // ??????
                    // ???????????????????????????
                    if (queryType.equals("cashier") && (/*StrUtil.isNotBlank(item.getCcheck()) || StrUtil.isNotBlank(item.getCdirector()) ||*/  StrUtil.equals(item.getIbook(), "1") || StrUtil.isNotBlank(item.getCbook())))
                        return false;
                    if (queryType.equals("review") && (/*StrUtil.isNotBlank(item.getCdirector()) ||*/ StrUtil.equals(item.getIbook(), "1") || StrUtil.isNotBlank(item.getCbook())))
                        return false;// ????????????????????? ??????
                    if (queryType.equals("sign") && (/*StrUtil.isBlank(item.getCcheck()) ||*/ StrUtil.equals(item.getIbook(), "1") || StrUtil.isNotBlank(item.getCbook())))
                        return false;// ?????????????????????
                    if (queryType.equals("book") && (StrUtil.isBlank(item.getCcheck()))) return false;// ?????????
                }
                return true;
            }).collect(Collectors.toList());
        }
        return list;
    }

    private List<Accvoucher> countFilter(List<Accvoucher> list, int maxNumber) {
        return list;
    }

    /**
     * ????????????????????????
     *
     * @param list
     * @param maxNumber
     * @return
     */
    private List<Accvoucher> countFilter2(List<Accvoucher> list, int maxNumber) {
        List<Accvoucher> filterList = new ArrayList<>();
        int count = 0;
        String thisOPZNumber = "";
        for (Accvoucher accvoucher : list) {
            if (!accvoucher.getInoId().equals(thisOPZNumber)) {
                thisOPZNumber = accvoucher.getInoId();
                count = 0;
            } else {
                ++count;
            }
            if (count < (maxNumber)) filterList.add(accvoucher);
        }
        for (Accvoucher accvoucher : filterList) {
            accvoucher.setInoId(Integer.parseInt(accvoucher.getInoId()) + "");
        }
        return filterList;
    }

    public static List<Accvoucher> splitList(List<Accvoucher> list, int pageNo, int pageSize, AtomicReference<Integer> titlesAR) {
        if (CollectionUtils.isEmpty(list)) {
            titlesAR.set(0);
            return new ArrayList<>();
        }
        // ????????????
        //List<Accvoucher> sortList = ListUtil.sort(list, (o1, o2) -> Integer.valueOf(o1.getInoId()).compareTo(Integer.valueOf(o2.getInoId())));
        int totalCount = list.size();
        titlesAR.set(totalCount);
        pageNo = pageNo - 1;
        int fromIndex = pageNo * pageSize;
        // ????????????????????????
        if (fromIndex >= totalCount) {
            return null;
        }
        int toIndex = ((pageNo + 1) * pageSize);
        if (toIndex > totalCount) {
            toIndex = totalCount;
        }
        return list.subList(fromIndex, toIndex);
    }

    /**
     * ??????????????????
     *
     * @return
     */
    @PostMapping("/breakNumTidy")
    public Mono<R> breakNumTidy(@RequestBody Map map) {
        String intervalStart = map.get("periodStart").toString().replaceAll("-", "");
        String intervalEnd = map.get("periodEnd").toString().replaceAll("-", "");
        Flux<Accvoucher> aMono = accvoucherRepository.findAllVoucherDetailByIyperiod(intervalStart.trim(), intervalEnd.trim());
        // ????????????Map
        Map<String, List<Accvoucher>> uniqueNumMap = new LinkedHashMap<>();
        return aMono.collectList().map(list -> {
            // ?????????????????????Map
            list.forEach(item -> uniqueNumMap.put(item.getDbillDate().substring(0, 7) + "==" + item.getInoId(), new ArrayList<>()));
            // ????????????????????????????????????
            list.stream().forEach(item -> uniqueNumMap.get(item.getDbillDate().substring(0, 7) + "==" + item.getInoId()).add(item));
            // ?????????????????????(inoid)
            AtomicInteger inoidIndex = new AtomicInteger(0);
            AtomicReference<String> thisYearMonth = new AtomicReference<>(list.get(0).getDbillDate().substring(0, 7));
            uniqueNumMap.forEach((k, accvouchers) -> {
                if (thisYearMonth.get().equals(accvouchers.get(0).getDbillDate().substring(0, 7))) {
                    inoidIndex.set(inoidIndex.get() + 1);
                } else {
                    inoidIndex.set(1);
                }
                for (int i = 0; i < accvouchers.size(); i++) {
                    String inoid = inoidIndex.get() + "";
                    accvouchers.get(i).setInoId(inoid);
                }
                thisYearMonth.set(accvouchers.get(0).getDbillDate().substring(0, 7));
            });
            // ??????????????????
            return uniqueNumMap;
        }).flatMapMany(map1 -> {
            List<Accvoucher> saveList = new ArrayList();
            map1.forEach((k, accvouchers) -> {
                saveList.addAll(accvouchers);
            });
            return accvoucherRepository.saveAll(saveList);
        }).collectList().map(o -> R.ok().setResult(o));
    }

    @PostMapping("/operateInvalid")
    public Mono<R> operateInvalid(@RequestBody Map map) {
        String intervalStart = map.get("periodStart").toString().replaceAll("-", "");
        String intervalEnd = map.get("periodEnd").toString().replaceAll("-", "");
        String type = map.get("type").toString().replaceAll("-", "");
        Flux<Accvoucher> aMono = accvoucherRepository.findAllVoucherInvalidByIyperiod(intervalStart.trim(), intervalEnd.trim());
        return aMono.collectList().flatMap(list -> type.equals("query") ? Mono.just(list.size()) : accvoucherRepository.deleteAll(list).thenReturn(1)).map(o -> R.ok().setResult(o));
    }

    // ???????????????
    private String generatePingZhengNum(int no) {
        return String.format("%04d", no);
    }

    private List<Accvoucher> filterUnCashierConditionCode(List<Accvoucher> acclist, List<CodeKemu> filterCodeList, String filterMark) {
        if (filterMark.equals("xj")) {
            filterCodeList = filterCodeList.stream().filter(item -> {
                if (item.getBcash().equals("1") || item.getBbank().equals("1")) return true;
                return false;
            }).collect(Collectors.toList());
        } else if (!filterMark.equals("all")) {
            String finalFilterMark = filterMark;
            filterCodeList = filterCodeList.stream().filter(item -> {
                if (finalFilterMark.equals("bcash") && item.getBcash().equals("1")) return true;
                if (finalFilterMark.equals("bbank") && item.getBbank().equals("1")) return true;
                if (finalFilterMark.equals("equivalence") && item.getBcashEquivalence().equals("1")) return true;
                return false;
            }).collect(Collectors.toList());
        }
        List<Accvoucher> results = new ArrayList<>();
        for (Accvoucher accvoucher : acclist) {
            for (CodeKemu codeKemu : filterCodeList) {
                if (accvoucher.getCcode().equals(codeKemu.getCcode())) results.add(accvoucher);
            }
        }
        return results;
    }

    private String getFieldValueByFieldName(String fieldName, Object object) {
        try {
            String firstLetter = fieldName.substring(0, 1).toUpperCase();
            String getter = "get" + firstLetter + fieldName.substring(1);
            Method method = object.getClass().getMethod(getter, new Class[]{});
            Object value = method.invoke(object, new Object[]{});
            return value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ?????????-?????? ??????????????????
     *
     * @param map
     * @return
     */
    @PostMapping("/findAllXuShiZhangList")
    public Mono<R> findAllXuShiZhangList(@RequestBody Map map) {
        //????????????
        if (map.keySet().size() == 2) {
            return Mono.just(R.ok().setResult(CollectOfUtils.mapof("total", 0, "items", new ArrayList<>())));
        }
        String queryMark = map.get("queryMark").toString();
        int page = Integer.parseInt(map.get("page").toString());
        int pageSize = Integer.parseInt(map.get("size").toString());
        Map<String, String> variableMap = ((HashMap<String, HashMap<String, String>>) map.get("condition")).get("variable");
        String intervalStart = variableMap.get("periodStart").replaceAll("-", "");
        String intervalEnd = variableMap.get("periodEnd").replaceAll("-", "");
        ;
        String dateStart = variableMap.get("dateStart");
        String dateEnd = variableMap.get("dateEnd");
        Mono<R> rMono = null;
        AtomicReference<Integer> totalAR = new AtomicReference();
        if (StrUtil.isNotBlank(intervalStart) && StrUtil.isNotBlank(intervalEnd)) {
            rMono = accvoucherRepository.findAllVoucherDetailByIyperiod(intervalStart, intervalEnd).collectList().map(list -> queryFilter(list, map)).map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
        } else if (StrUtil.isNotBlank(dateStart) && StrUtil.isNotBlank(dateEnd)) {
            rMono = accvoucherRepository.findAllVoucherDetailByDate(dateStart.trim(), dateEnd.trim()).collectList().map(list -> queryFilter(list, map)).map(list -> splitList(countFilter(list, 8), page, pageSize, totalAR)).map(list -> R.ok().setResult(CollectOfUtils.mapof("total", totalAR.get(), "items", list)));
        }
        return rMono;
    }

    @GetMapping("/findByUniqueCodeOrderByInid")
    public Mono<R> findByUniqueCodeOrderByInid(String uniqueCode) {
        return accvoucherRepository.findByUniqueCodeOrderByInid(uniqueCode).collectList().map(o -> R.ok().setResult(o));
    }

    @GetMapping("newDate")
    public Mono<R> findByUniqueCodeOrderByInid() {
        return accvoucherRepository.findFirstByImonthBetweenOrderByDbillDateDesc("01", "13").defaultIfEmpty(new Accvoucher()).map(o -> R.ok().setResult(o.getDbillDate()));
    }

    @PostMapping("/delSpecifyOld")
    public Mono<R> delSpecifyOld(@RequestBody Map map) {
        List<String> keys = (List<String>) map.get("selectedRowKeys");
        return accvoucherRepository.findAllByUniqueCodes(keys).filter(item -> item.getIfrag().equals("2") || item.getIfrag().equals("3")).collectList().flatMap(list -> accvoucherRepository.deleteAll(list).thenReturn(1)).map(o -> R.ok().setResult(o));
    }

    @PostMapping("/delSpecify")
    @Transactional
    public Mono<R> delSpecify(@RequestBody Map map) {
        List<String> keys = (List<String>) map.get("selectedRowKeys");
        String man = map.get("man").toString();
        return accvoucherRepository.findAllByUniqueCodes(keys).collectList().flatMap(list -> {
            List<Accvoucher> s = list.stream().filter(it -> StrUtil.isNotBlank(it.getCcashier()) || StrUtil.isNotBlank(it.getCcheck()) || StrUtil.isNotBlank(it.getCdirector()) || (StrUtil.isNotBlank(it.getIbook()) && it.getIbook().equals("1"))).filter(it -> !it.getIfrag().equals("2") && !it.getIfrag().equals("3")).collect(Collectors.toList());
            if (s.size() > 0) {
                return Mono.just(R.ok().setResult(CollectOfUtils.mapof("error", true, "list", s)));
            } else {
                return accvoucherRepository.deleteAll(list).thenReturn(1).flatMap(v -> {
                    List<AccvoucherDelete> saveDList = list.stream().map(e -> {
                        AccvoucherDelete d = new AccvoucherDelete();
                        BeanUtil.copyProperties(e, d, "id");
                        d.setDelName(man).setDelTime(DateUtil.now());
                        return d;
                    }).collect(Collectors.toList());
                    return accvoucherDeleteRepository.saveAll(saveDList).collectList();
                }).map(o -> R.ok().setResult(CollectOfUtils.mapof("error", false, "list", o)));
            }
        });
//        return accvoucherRepository.findAllByUniqueCodes(keys).filter(item -> item.getIfrag().equals("2") || item.getIfrag().equals("3")).collectList().flatMap(list -> accvoucherRepository.deleteAll(list).thenReturn(1)).map(o -> R.ok().setResult(o));
    }

    @GetMapping("dateTree")
    public Mono<R> dateTree(String yearMonth) {
        return accvoucherRepository.findAllVoucherTreeByDbillDateLike(yearMonth + "%").collectList().map(list -> {
            Map<String, Map<String, Set<String>>> maps = new HashMap<>();
            for (Accvoucher acc : list) {
                if (maps.containsKey(acc.getCsign())) {
                    Map<String, Set<String>> map = maps.get(acc.getCsign());
                    Set<String> elem = map.containsKey(acc.getDbillDate()) ? map.get(acc.getDbillDate()) : new HashSet<>();
                    elem.add(acc.getInoId());
                    map.put(acc.getDbillDate(), elem);
                    maps.put(acc.getCsign(), map);
                } else {
                    Set<String> elem = new HashSet<>();
                    elem.add(acc.getInoId());
                    maps.put(acc.getCsign(), MapUtil.of(acc.getDbillDate(), elem));
                }
            }
            return maps;
        }).map(o -> R.ok().setResult(o));
    }

    /******************** ?????????????????? ********************/
    @PostMapping("/checkError")
    public Mono<R> checkError(@RequestBody Map map) {
        if (map.keySet().size() < 2) return Mono.just(R.error());
        return accvoucherRepository.findAllByErrorPingZhengList(map.get("startDate").toString(), map.get("endDate").toString()).collectList().map(list -> {
            if (list.size() > 0) {
                return R.ok(list.stream().map(it -> it.getInoId()).collect(Collectors.toList()));
            } else {
                return R.ok();
            }
        });
    }

    @PostMapping("/checkSequenceDate")
    public Mono<R> checkSequenceDate(@RequestBody Map map) {
        if (map.keySet().size() < 2) return Mono.just(R.error());
        return accvoucherRepository.findAllByAllDateAndNumber(map.get("startDate").toString(), map.get("endDate").toString()).collectList().map(list -> {
            if (list.size() > 0) {
                int errorIndex = -1;
                for (int i = 0; i < list.size(); i++) {
                    Accvoucher up = i == 0 ? null : list.get(i - 1);
                    Accvoucher down = list.get(i);
                    if (null == up) continue;
                    if ((DateUtil.compare(DateUtil.parse(up.getDbillDate()), DateUtil.parse(down.getDbillDate())) > 0) || (Integer.parseInt(up.getInoId()) > Integer.parseInt(down.getInoId()))) {
                        errorIndex = i;
                        break;
                    }
                }
                if (errorIndex > -1) {
                    return R.ok(list.get(errorIndex).getDbillDate() + "  " + list.get(errorIndex).getInoId());
                } else {
                    return R.ok();
                }
            } else {
                return R.ok();
            }
        });
    }

    @PostMapping("/checkBreakSign")
    public Mono<R> checkBreakSign(@RequestBody Map map) {
        if (map.keySet().size() < 2) return Mono.just(R.error());
        return accvoucherRepository.findAllByAllDateAndNumber(map.get("startDate").toString(), map.get("endDate").toString()).collectList().map(list -> {
            if (list.size() > 0) {
                int errorIndex = -1;
                for (int i = 1; i <= list.size(); i++) {
                    if (i != Integer.parseInt(list.get(i - 1).getInoId())) {
                        errorIndex = i;
                        break;
                    }
                }
                if (errorIndex > -1) {
                    return R.ok(list.get(errorIndex - 1).getDbillDate() + "  " + list.get(errorIndex - 1).getInoId());
                } else {
                    return R.ok();
                }
            } else {
                return R.ok();
            }
        });
    }


    @PostMapping("/checkNumber")
    public Mono<R> checkNumber(@RequestBody Map map) {
        if (map.keySet().size() < 2) return Mono.just(R.error());
        String start = map.get("type").toString();
        return (start.equals("review") ? accvoucherRepository.findAllByUnReviewPingZhengList(map.get("startDate").toString(), map.get("endDate").toString()).collectList() : accvoucherRepository.findAllByUnIbookPingZhengList(map.get("startDate").toString(), map.get("endDate").toString()).collectList()).map(list -> {
            if (list.size() > 0) {
                return R.ok(new HashSet<>(list.stream().map(it -> it.getInoId()).collect(Collectors.toList())));
            } else {
                return R.ok();
            }
        });
    }

    /**
     * ??????????????????????????????
     *
     * @param map
     * @return
     */
    @PostMapping("/execVoucher")
    @Transactional
    public Mono<R> execVoucher(@RequestBody Map map) {
        if (map.keySet().size() < 2) return Mono.just(R.error());
        String start = map.get("type").toString();
        String operator = map.get("operator").toString();
        return (start.equals("review") ? accvoucherRepository.findAllByUnReviewPingZhengList(map.get("startDate").toString(), map.get("endDate").toString()).collectList() : accvoucherRepository.findAllByUnIbookPingZhengList(map.get("startDate").toString(), map.get("endDate").toString()).collectList()).flatMap(list -> {
            if (list.size() > 0) {
                for (Accvoucher accvoucher : list) {
                    if (start.equals("review")) {
                        accvoucher.setCcheck(operator);
                        accvoucher.setCcheckDate(DateUtil.today());
                    } else {
                        accvoucher.setIbook("1");
                        accvoucher.setCbook(operator);
                        accvoucher.setIbookDate(DateUtil.today());
                    }
                }
                return accvoucherRepository.saveAll(list).collectList().flatMap(l -> Mono.just(R.ok(l.size())));
            } else {
                return Mono.just(R.ok());
            }
        });
    }

    /******************** ??????????????????********************/


    @PostMapping("/finByMonthMaxInoId")
    public Mono<R> finByMonthMaxInoId(String imonth) {
        return accvoucherRepository.finByMonthMaxInoId(imonth).map(a -> R.ok().setResult(a));
    }

    @PostMapping("/findAllByIfrag")
    public Mono<R> findAllByIfrag(@RequestBody Map map) {
        return accvoucherRepository.findAllByIfrag().collectList().map(R::page);
    }
    @PostMapping("/findAllMxByKeys")
    public Mono<R> findAllMxByKeys(@RequestBody Map map) {
        return accvoucherRepository.findAllByUniqueCodes((List<String>) map.get("selectedRowKeys")).collectList().map(R::ok);
    }
    @PostMapping("/setRevision")
    public Mono<R> setRevision(@RequestBody Map<String,String> map) {
        Flux<Accvoucher> accvoucherFlux = accvoucherRepository.findAllByUniqueCode(map.get("uniqueCode")).map(it -> {
            it.setRevision(Integer.parseInt(map.get("revision")));
            return it;
        });
        return accvoucherRepository.saveAll(accvoucherFlux).then().map(R::ok);
    }

    @PostMapping("/findAllVoucherSummary")
    public Mono<R> findAllVoucherSummary(@RequestBody Map map) {
        return accvoucherRepository.findAllByCdigest(map.get("iyear").toString()).collectList().map(o->R.ok(o.stream().map(it->it.getCdigest()).distinct().collect(Collectors.toList())));
    }

    @PostMapping("/save")
    @Transactional
    public Mono<R> voucherSave(@RequestBody Map<String,String> map) {
        if (null == map.get("str") ) return Mono.just(R.error());
        List<Accvoucher> list = JSON.parseArray(map.get("str"), Accvoucher.class);
        String code = IdUtil.objectId();
        return null == list.get(0).getId()?accvoucherRepository.saveAll(list.stream().map(it->{it.setUniqueCode(code);return it;}).collect(Collectors.toList())).collectList().thenReturn(R.ok()):Mono.just(R.ok());
    }

    @PostMapping("findBillByCondition")
    public Mono<R> findBillByCondition(@RequestBody Map map) {
        if (map.keySet().size() == 0) return Mono.just(R.error());
        String type = map.get("type").toString();
        String iyear = map.get("iyear").toString();
        String action = map.get("action").toString();
        String currPdId = map.containsKey("curr") ? map.get("curr").toString() : "";
        return accvoucherRepository.findAllByIyearOrderByIyperiodAscInoIdAsc(iyear)
                .filter(it->true)
                .collectList().cache()
                .flatMap(list -> {
                    if (list.size() == 0) {
                        return Mono.just(R.ok());
                    } else {
                        Accvoucher master = null;
                        switch (action) {
                            case "curr":
                                master = list.get((list.stream().map(e -> e.getUniqueCode()).distinct().collect(Collectors.toList())).indexOf(Integer.parseInt(currPdId)));
                                break;
                            case "tail":
                                master = list.get(list.size() - 1);
                                break;
                            case "prev":
                            case "next":
                                if (StrUtil.isBlank(currPdId)) {
                                    master = action.equals("prev")?list.get(0):list.get(list.size() - 1);
                                } else {
                                    List<Accvoucher> collect = list.stream().filter(it -> it.getUniqueCode().equals(currPdId)).collect(Collectors.toList());
                                    List<String> inods = new ArrayList<>();
                                    for (Accvoucher accvoucher : list) {
                                        if (inods.contains(accvoucher.getInoId()+"=="+accvoucher.getUniqueCode())) continue;
                                        inods.add(accvoucher.getInoId()+"=="+accvoucher.getUniqueCode());
                                    }
                                    int index = collect.size() ==0 ?0:inods.indexOf(collect.get(0).getInoId()+"=="+collect.get(0).getUniqueCode());
                                    index = action.equals("prev")?(index == 0 ? 0 : index - 1):(index >= list.size() - 1 ? list.size() - 1 : index + 1);
                                    int finalIndex = index;
                                    master = list.stream().filter(it->(it.getInoId()+"=="+it.getUniqueCode()).equals(inods.get(finalIndex))).collect(Collectors.toList()).get(0);
                                }
                                break;
                            default:
                                master = list.get(0);
                                break;
                        }
                        Accvoucher finalMaster = master;
                        return Mono.just(R.ok(list.stream().filter(it->it.getUniqueCode().equals(finalMaster.getUniqueCode())).collect(Collectors.toList())));
                    }
                });
    }


    @PostMapping("findLastPingZhengInoid")
    public Mono<R> findLastPingZhengInoid(@RequestBody Map map) {
        if (map.keySet().size() == 0) return Mono.just(R.error());
        String[] dates= map.get("date").toString().split("-");
        String  csign= map.get("csign").toString();
        String  broken= map.get("broken").toString();
        String  sort= map.get("sort").toString();
        return  accvoucherRepository.findAllByCsignAndIyperiodLike(csign,("%"+(sort.equals("1")?dates[0]:dates[0]+dates[1])+"%")).collectList().flatMap(sourList->{
            if (sourList.size() == 0)return Mono.just(R.ok(1));
            String id = "";
            List<Integer> list = sourList.stream().map(it -> Integer.parseInt(it.getInoId())).distinct().collect(Collectors.toList());
            int max = list.get(list.size()-1);
            if (broken.equals("1")){
                for (int i = 1; i <= max; i++) {
                    if (!list.contains(i)){
                        id = i+"";
                        break;
                    }
                }
            }else {
                id = (max+1)+"";
            }
            return Mono.just(R.ok(id));
        });
    }

    @PostMapping("checkLastZhengInoid")
    public Mono<R> checkLastZhengInoid(@RequestBody Map map) {
        if (map.keySet().size() == 0) return Mono.just(R.error());
        String[] dates= map.get("date").toString().split("-");
        String  csign= map.get("csign").toString();
        String  code= map.get("code").toString();
        String  sort= map.get("sort").toString();
        return  accvoucherRepository.findAllByCsignAndIyperiodLike(csign,("%"+(sort.equals("1")?dates[0]:dates[0]+dates[1])+"%")).collectList().flatMap(sourList->{
            if (sourList.size() == 0)return Mono.just(R.ok(1));
            List<Integer> list = sourList.stream().map(it -> Integer.parseInt(it.getInoId())).distinct().collect(Collectors.toList());
            return Mono.just(R.ok(list.contains(code)?"1":"0"));
        });
    }

    @PostMapping("findPingZhengQjLastDate")
    public Mono<R> findPingZhengQjLastDate(@RequestBody Map map) {
        if (map.keySet().size() == 0) return Mono.just(R.error());
        String[] dates= map.get("date").toString().split("-");
        return  accvoucherRepository.findAllByIyperiodOrderByDesc(dates[0]+dates[1]).defaultIfEmpty("").flatMap(str->{
            if (StrUtil.hasBlank(str))return Mono.just(R.ok( map.get("date").toString()));
            return  Mono.just(R.ok(str));
        });
    }
}
