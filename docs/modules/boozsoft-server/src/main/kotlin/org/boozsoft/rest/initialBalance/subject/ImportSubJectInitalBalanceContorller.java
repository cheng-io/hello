package org.boozsoft.rest.initialBalance.subject;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.Objects;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.boozsoft.domain.entity.Accvoucher;
import org.boozsoft.domain.entity.Customer;
import org.boozsoft.domain.entity.Supplier;
import org.boozsoft.domain.entity.account.FuzhuHesuan;
import org.boozsoft.domain.entity.account.SettModes;
import org.boozsoft.domain.entity.account.SysDepartment;
import org.boozsoft.domain.entity.account.SysPsn;
import org.boozsoft.domain.entity.codekemu.CodeKemu;
import org.boozsoft.repo.*;
import org.boozsoft.repo.accountInfo.AccountInfoRepository;
import org.boozsoft.repo.codekemu.CodeKemuRepository;
import org.boozsoft.service.ProjectService;
import org.boozsoft.service.SubjectInitalBalanceService;
import org.boozsoft.util.ReflectionUtil;
import org.boozsoft.util.XlsUtils3;
import org.springbooz.core.tool.result.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple6;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @ClassName : SubjectInitialBalanceContorller
 * @Description : ????????????????????????
 * @Author : miao
 * @Date: 2021-05-24 17:06
 */
@Slf4j
@RestController
@RequestMapping("/subjectinitialBalance")
@Api(value = "????????????????????????", tags = "API?????????????????????????????????")
public class ImportSubJectInitalBalanceContorller {
    @Autowired
    IperiodRepository iperiodRepository;
    @Autowired
    AccountInfoRepository accountInfoRepository;
    @Autowired
    AccvoucherRepository accvoucherRepository;
    @Autowired
    SubjectInitalBalanceService service;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    CodeKemuRepository codeKemuRepository;
    @Autowired
    SettModesRepository settModesRepository;


    @PostMapping("/importInitalBalance")
    public Mono<R> listOCR(@RequestPart("file") FilePart filePartParm,String databasenum, String iyear,String mark) throws Exception {
        String[] aTemp = new String[]{"????????????", "????????????", "??????????????????", "??????????????????", "??????",/* "??????", */"????????????"/*,"??????????????????","???????????????","????????????","??????????????????"*/};
        if (null!=mark && mark.equals("1")) aTemp = ArrayUtil.append(aTemp,new String[]{"??????????????????", "??????????????????", "??????????????????", "??????????????????", "??????????????????", "??????????????????"});
        Path tempFilePath = Files.createTempFile("", new String(filePartParm.filename().getBytes("ISO-8859-1"), "UTF-8"));
        String[] finalATemp = aTemp;
        return Mono.just(filePartParm)
                // ?????????????????????
                .flatMap(filePart -> {
                    try {
                        return DataBufferUtils
                                .write(filePart.content(), AsynchronousFileChannel.open(tempFilePath, StandardOpenOption.WRITE), 0)
                                .doOnComplete(() -> log.info("????????????"))
                                .collectList()
                                .map(item -> tempFilePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return Mono.just("");
                })
                // ????????????excel??????
                .flatMap(objArr -> {
                    List<Object[]> list = null;
                    XlsUtils3 excelReader = new XlsUtils3(tempFilePath.toString());
                    try {
                        list = excelReader.getExcelObj(tempFilePath.toString(), finalATemp, 0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        assert tempFilePath != null;
                        try {
                            Files.delete(tempFilePath);
                        } catch (IOException e) {
                        }
                    }
                    return Mono.just(list);
                })
                // ?????? ??????????????????
                .flatMap(item -> {
                    // ???????????? ????????????
                    Map mapArr = new HashMap();
                    mapArr.put("excellist", item);   // excel?????????
                    mapArr.put("kmlist", "");        // ????????????
                    mapArr.put("error", "");
                    mapArr.put("accvoucherlist", "");
                    mapArr.put("code", "200");

                    // ????????????????????????
                    boolean columnsflg = true;
                    String[] a = finalATemp;
                    try{
                        for (int i = 0; i < a.length; i++) {
                            if (!item.get(0)[i].equals(a[i])) {
                                columnsflg = false;
                            }
                        }
                    }catch (Exception e){
                        // ?????????????????????????????????????????????
                        columnsflg = false;
                    }
                    if (!columnsflg) {
                        mapArr.put("error", "???????????????????????????");
                        mapArr.put("code", "401");
                    }
                    return Mono.just(mapArr);
                })
                // ??????????????????
                .flatMap(item -> {
                    return  codeKemuRepository.findAllByBendAndIyearOrderByCcode("1",iyear)
                            .collectList()
                            .flatMap(list -> {
                                item.put("kmlist", list);
                                return accvoucherRepository.findAllByIyperiodOrderByCcode(iyear + "00",databasenum).collectList()
                                        .flatMap(acc -> {
                                            item.put("accvoucherlist", acc);
                                            return settModesRepository.findByTenantId(databasenum).collectList()
                                                    .flatMap(settmodes->{
                                                        item.put("settmodeslist", settmodes);
                                                        return Mono.just(item);
                                                    });
                                        });
                            });
                })
                // ????????????????????????
                .flatMap(mapArr -> {
                    if(!mapArr.get("code").equals("401")){
                        // ????????????
                        List<SettModes> settmodeslist = (List<SettModes>) mapArr.get("settmodeslist");
                        // ??????list
                        List<CodeKemu> kmlist = (List<CodeKemu>) mapArr.get("kmlist");
                        // ??????list
                        List<Object[]> excellist = (List<Object[]>) mapArr.get("excellist");

                        /***********??????????????????************/
                        String empty_code = "";
                        /***********??????????????????************/
                        String last_code = "";
                        /***********????????????????????????************/
                        String code_state = "";
                        /***********?????????????????????************/
                        String md_mc_msg = "";
                        /***********??????????????????************/
                        String bnum_msg = "";
                        String price_msg = "";
                        /***********??????????????????************/
                        String currency_msg = "";
                        String currency_money_msg = "";
                        /***********????????????????????????************/
                        String jsfs_msg = "";

                        for (int i = 1; i < excellist.size(); i++) {
                            // *********************************************??????????????????????????????
                            if (StringUtils.isNotBlank(excellist.get(i)[0].toString())) {
                                int finalI = i;
                                // *********************************************??????????????????
                                List<CodeKemu> a = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                if (a.size() == 0) {
                                    empty_code += excellist.get(finalI)[0] + ",";
                                    break;
                                }
                                // *********************************************??????????????????
                                List<CodeKemu> b = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getBend().equals("1") && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                if (b.size() == 0) {
                                    last_code += excellist.get(finalI)[0] + ",";
                                    break;
                                }
                                // *********************************************????????????????????????
                                List<CodeKemu> c = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getFlag().equals("1") && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                if (c.size() == 0) {
                                    code_state += excellist.get(finalI)[0] + ",";
                                    break;
                                }

                                // *********************************************??????????????? ?????????????????????  <=0
                                if (Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[2].toString()) ? "0" : excellist.get(finalI)[2].toString()) != 0 && Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[3].toString()) ? "0" : excellist.get(finalI)[3].toString()) != 0) {
                                    md_mc_msg += excellist.get(finalI)[0] + ",";
                                    break;
                                }else{
                                    Double mmdd = Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[2].toString()) ? "0" : excellist.get(finalI)[2].toString());
                                    Double mmcc = Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[3].toString()) ? "0" : excellist.get(finalI)[3].toString());
                                    mmdd = fmtJiShui(mmdd);
                                    mmcc = fmtJiShui(mmcc);
                                    Double sum = 0.0;
                                    if(mmdd != 0.0 && mmcc != 0.0){
                                        if(mmdd > mmcc){
                                            mmdd = Double.parseDouble(fmtJiShui(mmdd - mmcc));
                                            mmcc = 0.0;
                                        }else{
                                            mmcc = Double.parseDouble(fmtJiShui(mmcc - mmcc));
                                            mmdd = 0.0;
                                        }
                                    }
                                    excellist.get(finalI)[2] = String.valueOf(mmdd);
                                    excellist.get(finalI)[3] = String.valueOf(mmcc);

                                    if("0.0".equals(String.valueOf(mmdd))){excellist.get(finalI)[2] = "0";}
                                    if("0.0".equals(String.valueOf(mmcc))){excellist.get(finalI)[3] = "0";}

                                    sum = Double.parseDouble(fmtJiShui(mmdd + mmcc));
                                    if(sum != 0.0){
                                        // *********************************************???????????????????????????????????????
                                        List<CodeKemu> d = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && "1".equals(code.getBnum()) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                        if (d != null && d.size() > 0) {
                                            // ?????????????????????  <=0
                                            if (Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[4].toString()) ? "0" : excellist.get(finalI)[4].toString()) == 0 ) {
                                                bnum_msg += excellist.get(finalI)[0] + ",";
                                                break;
                                            }
                                        }
                                        // *********************************************???????????????????????????????????????
                                        List<CodeKemu> e = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && "1".equals(code.getCurrency()) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                        if (e != null && e.size() > 0) {
                                            // ?????????????????????  <=0
//                                            if (Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[5].toString()) ? "0" : excellist.get(finalI)[5].toString()) <= 0) {
//                                                currency_msg += excellist.get(finalI)[0] + ",";
//                                                break;
//                                            }
                                            // ??????????????????:??????????????????0
                                            if (StringUtils.isBlank(excellist.get(finalI)[5].toString()) || Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[5].toString()) ? "0" : excellist.get(finalI)[5].toString()) == 0) {
                                                currency_money_msg += excellist.get(finalI)[0] + ",";
                                                break;
                                            }
                                        }
                                        // ??????????????????
//                                        if(settmodeslist != null && settmodeslist.size()>0){
//                                            if (StringUtils.isNotBlank(excellist.get(i)[7].toString())) {
//                                                List<SettModes> jsfs = settmodeslist.stream().filter(js ->"1".equals(js.getFlag()) && js.getSettModesCode().equals(excellist.get(finalI)[7].toString())).collect(Collectors.toList());
//                                                if(jsfs != null && jsfs.size()==0){
//                                                    jsfs_msg += excellist.get(finalI)[0] + ",";
//                                                    break;
//                                                }
//                                            }
//                                        }
                                    }
                                }
                            }
                        }

                        if (StringUtils.isNotBlank(empty_code)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + empty_code.substring(0, empty_code.length() - 1) + "????????????,??????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(last_code)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + last_code.substring(0, last_code.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(code_state)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + code_state.substring(0, code_state.length() - 1) + "??????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(md_mc_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + md_mc_msg.substring(0, md_mc_msg.length() - 1) + "????????????????????????????????????,???????????????0????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(bnum_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + bnum_msg.substring(0, bnum_msg.length() - 1) + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(price_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + price_msg.substring(0, price_msg.length() - 1) + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(currency_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + currency_msg.substring(0, currency_msg.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(currency_money_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + currency_money_msg.substring(0, currency_money_msg.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(jsfs_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + currency_money_msg.substring(0, currency_money_msg.length() - 1) + "??????????????????????????????????????????????????????????????????????????????");
                        }
                    }
                    return Mono.just(mapArr);
                })
                .flatMapMany(map -> {
                    List<Accvoucher> accvoucherlist = (List<Accvoucher>) map.get("accvoucherlist");
                    List<Accvoucher> newlist = new ArrayList<Accvoucher>();
                    List<Accvoucher> deletelist = new ArrayList<Accvoucher>();
                    // ??????list
                    List<Object[]> excellist = (List<Object[]>) map.get("excellist");
                    if(!map.get("code").equals("401")){
                        for (int i = 1; i < excellist.size(); i++) {
                            Accvoucher acc = new Accvoucher();
                            // *********************************************??????????????????
                            int finalI = i;
                            List<Accvoucher> a = accvoucherlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                            if (a.size() > 0) {
                                acc.setId(a.get(0).getId());
                            }
                            BigDecimal tMd = new BigDecimal(StringUtils.isBlank(excellist.get(finalI)[2].toString().trim())?"0":excellist.get(finalI)[2].toString().trim());
                            BigDecimal tMc = new BigDecimal(StringUtils.isBlank(excellist.get(finalI)[3].toString().trim())?"0":excellist.get(finalI)[3].toString().trim());
                            Integer tNds = Integer.parseInt(tMd.intValue() == 0?"0":StringUtils.isBlank(excellist.get(finalI)[4].toString().trim())?"0":excellist.get(finalI)[4].toString().trim());
                            Integer tNcs = Integer.parseInt(tMc.intValue() == 0?"0":StringUtils.isBlank(excellist.get(finalI)[4].toString().trim())?"0":excellist.get(finalI)[4].toString().trim());
                            BigDecimal dj = null;
                            if (((tMd.intValue() != 0 && tNds != 0) || (tMc.intValue() != 0 && tNcs != 0))){
                                dj = tMd.intValue() != 0?tMd.divide(new BigDecimal(tNds),2,BigDecimal.ROUND_HALF_UP).abs():tMc.divide(new BigDecimal(tNcs),2,BigDecimal.ROUND_HALF_UP).abs();
                                if ((tNds != 0 && tNds < 0 ) || (tNcs != 0 && tNcs < 0)){
                                    if (tMd.intValue() > 0){
                                        tMd = tMd.negate();
                                    }else if (tMc.intValue() > 0){
                                        tMc = tMc.negate();
                                    }
                                }
                            }
                            BigDecimal hl = null;
                            // ??????
                            BigDecimal tNfrat =  new BigDecimal(StringUtils.isBlank(excellist.get(finalI)[5].toString().trim())?"0":excellist.get(finalI)[5].toString().trim());
                            if (((tMd.intValue() != 0 ||tMc.intValue() != 0)  && tNfrat.intValue() != 0)){
                                hl = tMd.intValue() != 0?tMd.divide(tNfrat,2,BigDecimal.ROUND_HALF_UP).abs():tMc.divide(tNfrat,2,BigDecimal.ROUND_HALF_UP).abs();
                                if (tNfrat.intValue() < 0){
                                    if (tMd.intValue() > 0){
                                        tMd = tMd.negate();
                                    }else if (tMc.intValue() > 0){
                                        tMc = tMc.negate();
                                    }
                                }
                            }

                            BigDecimal lMd = new BigDecimal(mark.equals("1") && StringUtils.isNotBlank(excellist.get(finalI)[6].toString().trim())?excellist.get(finalI)[6].toString().trim():"0");
                            BigDecimal lMc = new BigDecimal(mark.equals("1") && StringUtils.isNotBlank(excellist.get(finalI)[7].toString().trim())?excellist.get(finalI)[7].toString().trim():"0");
                            Integer lNds = Integer.parseInt(lMd.intValue() == 0?"0":mark.equals("1") && StringUtils.isNotBlank(excellist.get(finalI)[8].toString().trim())?excellist.get(finalI)[8].toString().trim():"0");
                            Integer lNcs = Integer.parseInt(lMc.intValue() == 0?"0":mark.equals("1") && StringUtils.isNotBlank(excellist.get(finalI)[9].toString().trim())?excellist.get(finalI)[9].toString().trim():"0");
                            BigDecimal lWb = new BigDecimal(mark.equals("1") && StringUtils.isNotBlank(excellist.get(finalI)[10].toString().trim())?excellist.get(finalI)[2].toString().trim():"0");
                            BigDecimal lWc = new BigDecimal(mark.equals("1") && StringUtils.isNotBlank(excellist.get(finalI)[11].toString().trim())?excellist.get(finalI)[3].toString().trim():"0");


                            acc.setCcode(excellist.get(finalI)[0].toString().trim())
                                    .setCcodeName(excellist.get(finalI)[1].toString().trim())
                                    .setMd(tMd.toString())
                                    .setMc(tMc.toString())
                                    .setNdS(tNds.toString())
                                    .setNcS(tNcs.toString())
                                    .setCunitPrice(null == dj ? "0":dj.toString())
                                    .setMdF(null == hl ? "0":hl.toString())
                                    .setNfratMd(tMd.intValue() != 0 ?tNfrat.toString():"0")
                                    .setNfratMc(tMc.intValue() != 0 ?tNfrat.toString():"0")
                                    .setIyear(iyear)
                                    .setIperiod("00")
                                    .setImonth("00")
                                    .setIbook("0")
                                    .setIyperiod(iyear + "00")
//                                    .setPjId(StringUtils.isBlank(excellist.get(finalI)[8].toString().trim())?"":excellist.get(finalI)[8].toString().trim())
//                                    .setPjDate(StringUtils.isBlank(excellist.get(finalI)[9].toString().trim())?"":excellist.get(finalI)[9].toString().trim())
//                                    .setPjUnitName(StringUtils.isBlank(excellist.get(finalI)[10].toString().trim())?"":excellist.get(finalI)[10].toString().trim());
                                    .setLjMd(lMd.toString()).setLjMc(lMc.toString())
                                    .setLjSlMd(lNds+"")
                                    .setLjSlMc(lNcs+"")
                                    .setLjWbMd(lWb.toString())
                                    .setLjWbMc(lWc.toString());

                            // ????????? ?? ?????? = ??????
                            BigDecimal totalMoney=new BigDecimal(0);
                            BigDecimal sum=new BigDecimal(0);
                            BigDecimal price=new BigDecimal(0);
                            if(!"0".equals(acc.getMd())){
                                totalMoney=new BigDecimal(acc.getMd());
                            }else if(!"0".equals(acc.getMc())){
                                totalMoney=new BigDecimal(acc.getMc());
                            }
                            if(!"0".equals(acc.getNfratMd())){
                                sum=new BigDecimal(acc.getNfratMd());
                            }else if(!"0".equals(acc.getNfratMc())){
                                sum=new BigDecimal(acc.getNfratMc());
                            }
                  /*          try {
                                if(Double.parseDouble(sum.toString()) != 0.0){
                                    price=totalMoney.divide(sum);
                                }
                            }catch (Exception e) {
                                System.out.println("????????????");
                            }
                            acc.setCunitPrice(price.toString());*/
                            if(!"0".equals(acc.getMc()) || !"0".equals(acc.getMd())){
                                newlist.add(acc);
                            }
                            if("0".equals(acc.getMc()) && "0".equals(acc.getMd()) && acc.getId() != null){
                                deletelist.add(acc);
                            }
                        }
                    }
                    map.put("deletelist", deletelist);
                    return map.get("code").equals("401") ? Mono.just(map) : accvoucherRepository.saveAll(newlist).then(accvoucherRepository.deleteAll(deletelist)).then(Mono.just(map));
                })
                .collectList()
                .map(o -> R.ok().setResult(o));
    }

    private String jisuanStr = "";
    private Double elect;
    public String fmtJiShui(double d) {
        jisuanStr = d + "";
        elect = Double.parseDouble(jisuanStr);
        return new BigDecimal(String.valueOf(new BigDecimal(String.valueOf(elect)).setScale(6, BigDecimal.ROUND_HALF_UP))).setScale(2, BigDecimal.ROUND_HALF_UP).toString();
    }

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
    ProjectService projectService;
    @Autowired
    private DatabaseClient databaseClient;
    @Autowired
    FuzhuHesuanRepository fuzhuHesuanRepository;

    @PostMapping("/importInitalBalanceFz")
    @Transactional
    public Mono<R> listOCRFz(@RequestPart("file") FilePart filePartParm,String databasenum, String iyear,String mark,String titlesJson) throws Exception {
        Path tempFilePath = Files.createTempFile("", new String(filePartParm.filename().getBytes("ISO-8859-1"), "UTF-8"));
        List<String> titlesList = JSON.parseArray(titlesJson, String.class);
        boolean fx = mark.equals("1");

        AtomicReference<Integer> dateIndex = new AtomicReference(getCurrNameIndex("????????????",titlesList));  // ????????????
        AtomicReference<Integer> pzNumberIndex = new AtomicReference(getCurrNameIndex("?????????",titlesList));  // ?????????
        AtomicReference<Integer> pzzyIndex = new AtomicReference(getCurrNameIndex("????????????",titlesList));  // ????????????
        AtomicReference<Integer> mdIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> mcIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> numIndex = new AtomicReference(getCurrNameIndex("??????",titlesList));  // ??????
        AtomicReference<Integer> wbIndex = new AtomicReference(getCurrNameIndex("????????????",titlesList));  // ????????????
        AtomicReference<Integer> lmdIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> lmcIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> lnmdIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> lnmcIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> lwbmdIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> lwbmcIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ??????????????????
        AtomicReference<Integer> deptIndex = new AtomicReference(getCurrNameIndex("??????"+(fx?"??????":"??????"),titlesList));  // ??????
        AtomicReference<Integer> userIndex = new AtomicReference(getCurrNameIndex("??????"+(fx?"??????":"??????"),titlesList));  // ??????
        AtomicReference<Integer> custIndex = new AtomicReference(getCurrNameIndex("??????"+(fx?"??????":"??????"),titlesList));  // ??????
        AtomicReference<Integer> supIndex = new AtomicReference(getCurrNameIndex("?????????"+(fx?"??????":"??????"),titlesList));  // ?????????
        AtomicReference<Integer> itemClassIndex = new AtomicReference(getCurrNameIndex("??????????????????",titlesList));  // ????????????
        AtomicReference<Integer> itemIndex = new AtomicReference(getCurrNameIndex("??????"+(fx?"??????":"??????"),titlesList));  // ??????
        AtomicReference<Map<String,Integer>> cdfinesIndex = new AtomicReference(new HashMap<>()); // ????????????
        return Mono.just(filePartParm)
                // ?????????????????????
                .flatMap(filePart -> {
                    try {
                        return DataBufferUtils
                                .write(filePart.content(), AsynchronousFileChannel.open(tempFilePath, StandardOpenOption.WRITE), 0)
                                .doOnComplete(() -> log.info("????????????"))
                                .collectList()
                                .map(item -> tempFilePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return Mono.just("");
                })
                // ????????????excel??????
                .flatMap(objArr -> {
                    List<Object[]> list = null;
                    XlsUtils3 excelReader = new XlsUtils3(tempFilePath.toString());
                    try {
                        list = excelReader.getExcelObj(tempFilePath.toString(), titlesList.toArray(new String[titlesList.size()]), 0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        assert tempFilePath != null;
                        try {
                            Files.delete(tempFilePath);
                        } catch (IOException e) {
                        }
                    }
                    return Mono.just(list);
                })
                // ?????? ??????????????????
                .flatMap(item -> {
                    // ???????????? ????????????
                    Map mapArr = new HashMap();
                    mapArr.put("excellist", item);   // excel?????????
                    mapArr.put("kmlist", "");        // ????????????
                    mapArr.put("error", "");
                    mapArr.put("accvoucherlist", "");
                    mapArr.put("code", "200");

                    // ????????????????????????
                    boolean columnsflg = true;
                    String[] a = titlesList.toArray(new String[titlesList.size()]);
                    try{
                        for (int i = 0; i < a.length; i++) {
                            if (!item.get(0)[i].equals(a[i])) {
                                columnsflg = false;
                            }
                        }
                    }catch (Exception e){
                        // ?????????????????????????????????????????????
                        columnsflg = false;
                    }
                    if (!columnsflg) {
                        mapArr.put("error", "???????????????????????????");
                        mapArr.put("code", "401");
                    }
                    if (columnsflg && itemClassIndex.get() > 0){
                        Set<String> projects = new HashSet<>();
                        if (item.size() > 0){
                            for (Object[] objects : item) {
                                if (StrUtil.isNotBlank(objects[itemClassIndex.get()].toString()))projects.add(objects[itemClassIndex.get()].toString());
                            }
                        }
                        mapArr.put("projects",projects);
                    }else {
                        mapArr.put("projects",new HashSet<>());
                    }
                    return Mono.just(mapArr);
                })
                // ??????????????????
                .flatMap(item -> {
                            return codeKemuRepository.findAllByBendAndIyearOrderByCcode("1", iyear)
                                    .collectList()
                                    .flatMap(list -> {
                                        item.put("kmlist", list);
                                        Set<String> projects = (Set<String>) item.get("projects");
                                        return fuzhuHesuanRepository.findAll().filter(item1 -> titlesList.contains(item1.getCname() + (fx ? "??????" : "??????"))).collectList()
                                                .flatMap(tups -> {
                                                    item.put("fzhsList", tups);
                                                    if (tups.size() > 0) {
                                                        // ????????????????????????
                                                        Map<String, Integer> map = cdfinesIndex.get();
                                                        for (FuzhuHesuan fz : tups) {
                                                            map.put("cdfine" + fz.getCdfine(), getCurrNameIndex(fz.getCname()+(fx ? "??????" : "??????"), titlesList));
                                                        }
                                                        cdfinesIndex.set(map);
                                                    }
                                                    // ??????????????????
                                                    Mono<List<SysPsn>> geSets = psnRepository.findAllPsnCodeOrPsnNameByFlag().collectList();
                                                    Mono<List<SysDepartment>> bmSets = departmentRepository.findAllDeptCodeOrDeptNameByFlag().collectList();
                                                    Mono<List<Customer>> khSets = customerRepository.findAllCustCodeOrCustNameByFlag().collectList();
                                                    Mono<List<Supplier>> gysSets = supplierRepository.findAllCustCodeOrCustNameByFlag().collectList();
                                                    Mono<Map<String, Set<String>>> proMap = null;
                                                    if (projects.size() > 0) {
                                                        proMap = Mono.just(new HashMap<String, Set<String>>()).map(maps ->
                                                                Flux.fromIterable(projects)
                                                                        .flatMap(proNum -> projectService.findByProjectCodeAndValue(proNum, mark)
                                                                                .doOnNext(sets -> maps.put(proNum, sets))
                                                                        ).collectList().map(list1 -> maps)
                                                        ).flatMap(a -> a);
                                                    } else {
                                                        proMap = Mono.just(new HashMap<>());
                                                    }
                                                    // ?????????????????????????????????
                                                    Mono<Map<String, List<String[]>>> customizes = Flux.fromIterable(tups).flatMap(fzItem -> {
                                                        String key = "cdfine" + fzItem.getCdfine();
                                                        String tableName = fzItem.getCankaoDuixiangTable();
                                                        String queryWhere = fzItem.getCankaoDuixiangWhere();
                                                        String keyColName = fzItem.getCankaoDuixiangKey();
                                                        String codeColName = fzItem.getCankaoDuixiangCode();
                                                        String nameColNmae = fzItem.getCankaoDuixiangLabel();
                                                        // ??????????????????????????????
       //                                                accvoucherRepository.findAllByCurrDynamic(tableName, queryWhere, keyColName, mark.equals("1") ? nameColNmae : codeColName).collectList()
                                                        Mono<List<Map<String, Object>>> listMono = databaseClient.sql(StrUtil.format("select {} as key,{} as label from {} {} ORDER By id Asc", keyColName, mark.equals("1") ? nameColNmae : codeColName, tableName, queryWhere))
                                                                .fetch()
                                                                .all().collectList();
                                                        return listMono.map(kvList -> {
                                                            List<String[]> objects = new ArrayList<>();
                                                            for (Map<String, Object> stringObjectMap : kvList) {
                                                               String []o = {stringObjectMap.get("key").toString(), stringObjectMap.get("label").toString()} ;
                                                               objects.add(o);
                                                            }
                                                           return MapUtil.of(key,objects);
                                                        });
                                                    }).collectList().flatMap(fzList -> {
                                                        Map<String, List<String[]>> resutlMap = new HashMap<>();
                                                        for (Map<String, List<String[]>> map : fzList) {
                                                            List<String> keys = new ArrayList<>(map.keySet());
                                                            resutlMap.put(keys.get(0), map.get(keys.get(0)));
                                                        }
                                                        return Mono.just(resutlMap);
                                                    });
                                                    // ??????????????????
                                                    return Mono.zip(geSets, bmSets, khSets, gysSets, proMap, customizes).flatMap(zips -> {
                                                        item.put("codeSets", zips);
                                                        return Mono.just(item);
                                                    });
                                                });
                                    });
                        }
                )
                // ????????????????????????
                .flatMap(mapArr -> {
                    if(!mapArr.get("code").equals("401")){
                        // ?????????
                        Tuple6<List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, Map<String, Set<String>>, Map<String, List<String[]>>> beingSets =
                                (Tuple6<List<SysPsn>, List<SysDepartment>, List<Customer>, List<Supplier>, Map<String, Set<String>>, Map<String, List<String[]>>>)mapArr.get("codeSets");
                        // ??????list
                        List<CodeKemu> kmlist = (List<CodeKemu>) mapArr.get("kmlist");
                        List<FuzhuHesuan> fzhsList = (List<FuzhuHesuan>) mapArr.get("fzhsList");
                        // ??????list
                        List<Object[]> excellist = (List<Object[]>) mapArr.get("excellist");

                        /***********??????????????????************/
                        String empty_code = "";
                        /***********??????????????????************/
                        String last_code = "";
                        /***********????????????????????????************/
                        String code_state = "";
                        /***********?????????????????????************/
                        String md_mc_msg = "";
                        /***********??????????????????************/
                        String bnum_msg = "";
                        String price_msg = "";
                        /***********??????????????????************/
                        String currency_msg = "";
                        String currency_money_msg = "";
                        /***********??????????????????************/
                        String erorrCodes = "";
                        String jsfs_blank_msg = ""; // ??????
                        String jsfs_msg = ""; // ??????

                        String proOne = "";
                        String proTwo = "";
                        String prothree = "";
                        String profour = "";

                        String fz_msg = "";

                        for (int i = 1; i < excellist.size(); i++) {
                            // *********************************************??????????????????????????????
                            if (StringUtils.isNotBlank(excellist.get(i)[0].toString())) {
                                int finalI = i;
                                // *********************************************??????????????????
                                List<CodeKemu> a = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                if (a.size() == 0) {
                                    empty_code += excellist.get(finalI)[0] + ",";
                                    break;
                                }
                                // *********************************************??????????????????
                                List<CodeKemu> b = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getBend().equals("1") && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                if (b.size() == 0) {
                                    last_code += excellist.get(finalI)[0] + ",";
                                    break;
                                }
                                // *********************************************????????????????????????
                                List<CodeKemu> c = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && code.getFlag().equals("1") && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                if (c.size() == 0) {
                                    code_state += excellist.get(finalI)[0] + ",";
                                    break;
                                }

                                // *********************************************??????????????? ?????????????????????  <=0
                                if (Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[mdIndex.get()].toString()) ? "0" : excellist.get(finalI)[mdIndex.get()].toString()) != 0 && Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[mcIndex.get()].toString()) ? "0" : excellist.get(finalI)[mcIndex.get()].toString()) != 0) {
                                    md_mc_msg += excellist.get(finalI)[0] + ",";
                                    break;
                                }else{
                                    Double mmdd = Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[mdIndex.get()].toString()) ? "0" : excellist.get(finalI)[mdIndex.get()].toString());
                                    Double mmcc = Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[mcIndex.get()].toString()) ? "0" : excellist.get(finalI)[mcIndex.get()].toString());
                                    mmdd = fmtJiShui(mmdd);
                                    mmcc = fmtJiShui(mmcc);
                                    Double sum = 0.0;
                                    if(mmdd != 0.0 && mmcc != 0.0){
                                        if(mmdd > mmcc){
                                            mmdd = Double.parseDouble(fmtJiShui(mmdd - mmcc));
                                            mmcc = 0.0;
                                        }else{
                                            mmcc = Double.parseDouble(fmtJiShui(mmcc - mmcc));
                                            mmdd = 0.0;
                                        }
                                    }
                                    excellist.get(finalI)[mdIndex.get()] = String.valueOf(mmdd);
                                    excellist.get(finalI)[mcIndex.get()] = String.valueOf(mmcc);

                                    if("0.0".equals(String.valueOf(mmdd))){excellist.get(finalI)[mdIndex.get()] = "0";}
                                    if("0.0".equals(String.valueOf(mmcc))){excellist.get(finalI)[mcIndex.get()] = "0";}

                                    sum = Double.parseDouble(fmtJiShui(mmdd + mmcc));
                                    if(sum != 0.0){
                                        // *********************************************???????????????????????????????????????
                                        List<CodeKemu> d = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && "1".equals(code.getBnum()) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                        if (d != null && d.size() > 0) {
                                            // ?????????????????????  <=0
                                            if (Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[numIndex.get()].toString()) ? "0" : excellist.get(finalI)[numIndex.get()].toString()) == 0 ) {
                                                bnum_msg += excellist.get(finalI)[0] + ",";
                                                break;
                                            }
                                        }
                                        // *********************************************???????????????????????????????????????
                                        List<CodeKemu> e = kmlist.stream().filter(code -> code.getCcode().equals(excellist.get(finalI)[0]) && "1".equals(code.getCurrency()) && code.getIyear().equals(iyear)).collect(Collectors.toList());
                                        if (e != null && e.size() > 0) {
                                            // ?????????????????????  <=0
//                                            if (Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[5].toString()) ? "0" : excellist.get(finalI)[5].toString()) <= 0) {
//                                                currency_msg += excellist.get(finalI)[0] + ",";
//                                                break;
//                                            }
                                            // ??????????????????:??????????????????0
                                            if (StringUtils.isBlank(excellist.get(finalI)[wbIndex.get()].toString()) || Double.valueOf(StringUtils.isBlank(excellist.get(finalI)[wbIndex.get()].toString()) ? "0" : excellist.get(finalI)[wbIndex.get()].toString()) == 0) {
                                                currency_money_msg += excellist.get(finalI)[0] + ",";
                                                break;
                                            }
                                        }
                                        CodeKemu thisKemu = a.get(0);
                                        // ??????????????????
                                        if (thisKemu.getBdept().equals("1")) {
                                            String dValue = (null == excellist.get(finalI)[deptIndex.get()] ? "" : excellist.get(finalI)[deptIndex.get()].toString());
                                            List<SysDepartment> deptCollect = beingSets.getT2().stream().filter(item -> (fx ? item.getDeptName().equals(dValue) : item.getDeptCode().equals(dValue))).collect(Collectors.toList());
                                            if (org.springbooz.core.tool.utils.StringUtils.isBlank(dValue)) {
                                                jsfs_blank_msg +=  "??????,";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (deptCollect.size() == 0) {
                                                jsfs_msg += excellist.get(finalI)[deptIndex.get()] + ",";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else {
                                                excellist.get(finalI)[deptIndex.get()] = deptCollect.get(0).getUniqueCode();
                                            }
                                        }else {
                                            excellist.get(finalI)[deptIndex.get()] = "";
                                        }

                                        if (thisKemu.getBperson().equals("1")) {
                                            String grValue = (null == excellist.get(finalI)[userIndex.get()] ? "" :excellist.get(finalI)[userIndex.get()].toString());
                                            List<SysPsn> psnCollect = beingSets.getT1().stream().filter(item -> (fx ? item.getPsnName().equals(grValue) : item.getPsnCode().equals(grValue))).collect(Collectors.toList());
                                            if (org.springbooz.core.tool.utils.StringUtils.isBlank(grValue)) {
                                                jsfs_blank_msg +=  "??????,";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (psnCollect.size() == 0) {
                                                jsfs_msg += excellist.get(finalI)[userIndex.get()] + ",";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else {
                                                excellist.get(finalI)[userIndex.get()] = psnCollect.get(0).getUniqueCode();
                                            }
                                        }else {
                                            excellist.get(finalI)[userIndex.get()] = "";
                                        }

                                        if (thisKemu.getBcus().equals("1")) {
                                            String cValue = (null == excellist.get(finalI)[custIndex.get()] ? "" : excellist.get(finalI)[custIndex.get()].toString());
                                            List<Customer> customerCollect = beingSets.getT3().stream().filter(item -> (fx ? (item.getCustName().equals(cValue) || item.getCustAbbname().equals(cValue)) : item.getCustCode().equals(cValue))).collect(Collectors.toList());
                                            if (org.springbooz.core.tool.utils.StringUtils.isBlank(cValue)) {
                                                jsfs_blank_msg +=  "??????,";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (customerCollect.size() == 0) {
                                                jsfs_msg += excellist.get(finalI)[custIndex.get()] + ",";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else {
                                                excellist.get(finalI)[custIndex.get()] = customerCollect.get(0).getUniqueCode();
                                            }
                                        }else{
                                            excellist.get(finalI)[custIndex.get()] = "";
                                        }

                                        if (thisKemu.getBsup().equals("1")) {
                                            String gyValue = (null == excellist.get(finalI)[supIndex.get()] ? "" : excellist.get(finalI)[supIndex.get()].toString());
                                            List<Supplier> supCollect = beingSets.getT4().stream().filter(item -> (fx ? (item.getCustName().equals(gyValue) || item.getCustAbbname().equals(gyValue)) : item.getCustCode().equals(gyValue))).collect(Collectors.toList());
                                            if (org.springbooz.core.tool.utils.StringUtils.isBlank(gyValue)) {
                                                jsfs_blank_msg +=  "?????????,";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (supCollect.size() == 0) {
                                                jsfs_msg += excellist.get(finalI)[supIndex.get()] + ",";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else {
                                                excellist.get(finalI)[supIndex.get()] = supCollect.get(0).getUniqueCode();
                                            }
                                        }else{
                                            excellist.get(finalI)[supIndex.get()] = "";
                                        }

                                        if (thisKemu.getBitem().equals("1")) {
                                            String pValue = (null == excellist.get(finalI)[itemIndex.get()] ? "" : excellist.get(finalI)[itemIndex.get()].toString());
                                            String pdValue = (null == excellist.get(finalI)[itemClassIndex.get()] ? "" : excellist.get(finalI)[itemClassIndex.get()].toString());
                                            if (org.springbooz.core.tool.utils.StringUtils.isBlank(pValue)) {
                                                proOne +=   "??????,";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (org.springbooz.core.tool.utils.StringUtils.isBlank(pdValue)) {
                                                proTwo +=  "????????????,";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (org.springbooz.core.tool.utils.StringUtils.isNotBlank(pdValue) && null == beingSets.getT5().get(pdValue)) {
                                                prothree += excellist.get(finalI)[itemClassIndex.get()] + ",";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (org.springbooz.core.tool.utils.StringUtils.isNotBlank(pdValue) && beingSets.getT5().get(pdValue).stream().filter(item -> item.split("--")[0].equals(pValue)).collect(Collectors.toList()).size() == 0) {
                                                profour += excellist.get(finalI)[itemIndex.get()] + ",";
                                                erorrCodes += excellist.get(finalI)[0] + ",";
                                                break;
                                            } else if (beingSets.getT5().get(pdValue).stream().filter(item -> item.split("--")[0].equals(pValue)).collect(Collectors.toList()).size() > 0) {
                                                String s = beingSets.getT5().get(pdValue).stream().filter(item -> item.split("--")[0].equals(pValue)).collect(Collectors.toList()).get(0);
                                                excellist.get(finalI)[itemIndex.get()] = s.split("--")[1];
                                            }
                                        }else{
                                            excellist.get(finalI)[itemIndex.get()] = "";
                                        }
                                        // ???????????????
                                        for (FuzhuHesuan fuzhuHesuan : fzhsList) {
                                            String thisCffine =  "cdfine"+fuzhuHesuan.getCdfine();
                                            String isCdfine = ReflectionUtil.getValue(thisKemu, thisCffine).toString();
                                            if (isCdfine.equals("1")){
                                                // ????????? ??????
                                                String thisfzValue =  excellist.get(finalI)[cdfinesIndex.get().get(thisCffine)].toString();
                                                if (org.springbooz.core.tool.utils.StringUtils.isBlank(thisfzValue)) {
                                                    fz_msg +=  ( fuzhuHesuan.getCname()+",");
                                                    erorrCodes += excellist.get(finalI)[0] + ",";
                                                    break;
                                                }  else if (org.springbooz.core.tool.utils.StringUtils.isNotBlank(thisfzValue) && null == beingSets.getT6().get(thisCffine) && beingSets.getT6().get(thisCffine).stream().filter(strs -> strs[1].equals(thisfzValue)).collect(Collectors.toList()).size() == 0) {
                                                    fz_msg += excellist.get(finalI)[cdfinesIndex.get().get(thisCffine)] + ",";
                                                    erorrCodes += excellist.get(finalI)[0] + ",";
                                                    break;
                                                } else { // ?????????
                                                    excellist.get(finalI)[cdfinesIndex.get().get(thisCffine)] = beingSets.getT6().get(thisCffine).stream().filter(strs -> strs[1].equals(thisfzValue)).collect(Collectors.toList()).get(0)[0];
                                                }
                                            }else {
                                                excellist.get(finalI)[cdfinesIndex.get().get(thisCffine)] = "";
                                            }
                                        }
                                        excellist.get(finalI)[1]= thisKemu.getCcodeName(); // ??????????????????????????????????????????
                                    }
                                }
                            }
                        }

                        if (StringUtils.isNotBlank(empty_code)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + empty_code.substring(0, empty_code.length() - 1) + "????????????,??????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(last_code)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + last_code.substring(0, last_code.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(code_state)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + code_state.substring(0, code_state.length() - 1) + "??????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(md_mc_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + md_mc_msg.substring(0, md_mc_msg.length() - 1) + "????????????????????????????????????,???????????????0????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(bnum_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + bnum_msg.substring(0, bnum_msg.length() - 1) + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(price_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + price_msg.substring(0, price_msg.length() - 1) + "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(currency_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + currency_msg.substring(0, currency_msg.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        } else if (StringUtils.isNotBlank(currency_money_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + currency_money_msg.substring(0, currency_money_msg.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(jsfs_blank_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "??????"+jsfs_blank_msg.substring(0, jsfs_blank_msg.length() - 1)+"????????????"+(fx?"??????":"??????")+"????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(jsfs_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "????????????"+(fx?"??????":"??????")+"???"+jsfs_msg.substring(0, jsfs_msg.length() - 1)+"????????????????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(proOne)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "?????????????????????"+(fx?"??????":"??????")+"?????????????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(proTwo)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "??????????????????"+(fx?"??????":"??????")+"?????????????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(prothree)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "????????????????????????????????????" + prothree.substring(0, prothree.length() - 1) + "????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }else if (StringUtils.isNotBlank(profour)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "?????????" + (fx ? "??????" : "??????") + "???" + profour.substring(0, profour.length() - 1) + "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????" + profour.substring(0, profour.length() - 1) + "???");
                        } else if (StringUtils.isNotBlank(fz_msg)) {
                            mapArr.put("code", "401");
                            mapArr.put("error", "?????????????????????????????????" + erorrCodes.substring(0, erorrCodes.length() - 1) + "?????????????????????" + (fx ? "??????" : "??????") + "??????" + fz_msg.substring(0, fz_msg.length() - 1) + "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }
                    }
                    return Mono.just(mapArr);
                })
                .flatMapMany(map -> {
                    List<Accvoucher> newlist = new ArrayList<>();
                    // ??????list
                    List<Object[]> excellist = (List<Object[]>) map.get("excellist");
                    if(!map.get("code").equals("401")){
                        for (int i = 1; i < excellist.size(); i++) {
                            Accvoucher acc = new Accvoucher();
                            // *********************************************??????????????????
                            int finalI = i;
                            BigDecimal tMd = new BigDecimal(StringUtils.isBlank(excellist.get(finalI)[mdIndex.get()].toString().trim())?"0":excellist.get(finalI)[mdIndex.get()].toString().trim());
                            BigDecimal tMc = new BigDecimal(StringUtils.isBlank(excellist.get(finalI)[mcIndex.get()].toString().trim())?"0":excellist.get(finalI)[mcIndex.get()].toString().trim());
                            Integer tNds = Integer.parseInt(tMd.intValue() == 0?"0":StringUtils.isBlank(excellist.get(finalI)[numIndex.get()].toString().trim())?"0":excellist.get(finalI)[numIndex.get()].toString().trim());
                            Integer tNcs = Integer.parseInt(tMc.intValue() == 0?"0":StringUtils.isBlank(excellist.get(finalI)[numIndex.get()].toString().trim())?"0":excellist.get(finalI)[numIndex.get()].toString().trim());

                            BigDecimal lMd = new BigDecimal(lmdIndex.get() > -1 && StringUtils.isNotBlank(excellist.get(finalI)[lmdIndex.get()].toString().trim())?excellist.get(finalI)[lmdIndex.get()].toString().trim():"0");
                            BigDecimal lMc = new BigDecimal(lmcIndex.get() > -1 && StringUtils.isNotBlank(excellist.get(finalI)[lmcIndex.get()].toString().trim())?excellist.get(finalI)[lmcIndex.get()].toString().trim():"0");
                            Integer lNds = Integer.parseInt(lMd.intValue() == 0?"0":lnmdIndex.get() > -1 && StringUtils.isNotBlank(excellist.get(finalI)[lnmdIndex.get()].toString().trim())?excellist.get(finalI)[lnmdIndex.get()].toString().trim():"0");
                            Integer lNcs = Integer.parseInt(lMc.intValue() == 0?"0":lnmcIndex.get() > -1 && StringUtils.isNotBlank(excellist.get(finalI)[lnmcIndex.get()].toString().trim())?excellist.get(finalI)[lnmcIndex.get()].toString().trim():"0");
                            BigDecimal lWb = new BigDecimal(lwbmdIndex.get() > -1 &&  StringUtils.isNotBlank(excellist.get(finalI)[lwbmdIndex.get()].toString().trim())?excellist.get(finalI)[lwbmdIndex.get()].toString().trim():"0");
                            BigDecimal lWc = new BigDecimal(lwbmcIndex.get() > -1 &&  StringUtils.isNotBlank(excellist.get(finalI)[lwbmcIndex.get()].toString().trim())?excellist.get(finalI)[lwbmcIndex.get()].toString().trim():"0");
                            BigDecimal dj = null;
                            if (((tMd.intValue() != 0 && tNds != 0) || (tMc.intValue() != 0 && tNcs != 0))){
                                dj = tMd.intValue() != 0?tMd.divide(new BigDecimal(tNds),2,BigDecimal.ROUND_HALF_UP).abs():tMc.divide(new BigDecimal(tNcs),2,BigDecimal.ROUND_HALF_UP).abs();
                                if ((tNds != 0 && tNds < 0 ) || (tNcs != 0 && tNcs < 0)){
                                    if (tMd.intValue() > 0){
                                        tMd = tMd.negate();
                                    }else if (tMc.intValue() > 0){
                                        tMc = tMc.negate();
                                    }
                                }
                            }
                            BigDecimal hl = null;
                            // ??????
                            BigDecimal tNfrat =  new BigDecimal(StringUtils.isBlank(excellist.get(finalI)[wbIndex.get()].toString().trim())?"0":excellist.get(finalI)[wbIndex.get()].toString().trim());
                            if (((tMd.intValue() != 0 ||tMc.intValue() != 0)  && tNfrat.intValue() != 0)){
                                hl = tMd.intValue() != 0?tMd.divide(tNfrat,2,BigDecimal.ROUND_HALF_UP).abs():tMc.divide(tNfrat,2,BigDecimal.ROUND_HALF_UP).abs();
                                if (tNfrat.intValue() < 0){
                                    if (tMd.intValue() > 0){
                                        tMd = tMd.negate();
                                    }else if (tMc.intValue() > 0){
                                        tMc = tMc.negate();
                                    }
                                }
                            }
                            String userV = userIndex.get() == -1 || StrUtil.isBlank(excellist.get(finalI)[userIndex.get()].toString()) ? null : excellist.get(finalI)[userIndex.get()].toString();
                            String custV = custIndex.get() == -1 || StrUtil.isBlank(excellist.get(finalI)[custIndex.get()].toString()) ? null : excellist.get(finalI)[custIndex.get()].toString();
                            String supV = supIndex.get() == -1 ||  StrUtil.isBlank(excellist.get(finalI)[supIndex.get()].toString()) ? null : excellist.get(finalI)[supIndex.get()].toString();
                            acc.setCcode(excellist.get(finalI)[0].toString().trim())
                                    .setCcodeName(excellist.get(finalI)[1].toString().trim())
                                    .setMd(tMd.toString())
                                    .setMc(tMc.toString())
                                    .setNdS(tNds.toString())
                                    .setNcS(tNcs.toString())
                                    .setCunitPrice(null == dj ? "0":dj.toString())
                                    .setMdF(null == hl ? "0":hl.toString())
                                    .setNfratMd(tMd.intValue() != 0?tNfrat.toString():"0")
                                    .setNfratMc(tMc.intValue() != 0?tNfrat.toString():"0")
                                    .setLjMd(lMd.toString()).setLjMc(lMc.toString())
                                    .setLjSlMd(lNds+"")
                                    .setLjSlMc(lNcs+"")
                                    .setLjWbMd(lWb.toString())
                                    .setLjWbMc(lWc.toString())
                                    .setIyear(iyear)
                                    .setIperiod("21")
                                    .setImonth("21")
                                    .setIbook("0")
                                    .setIyperiod(iyear + "21")
                                    .setCdeptId(deptIndex.get() == -1 || StrUtil.isBlank(excellist.get(finalI)[deptIndex.get()].toString())?null:excellist.get(finalI)[deptIndex.get()].toString())
                                    .setCpersonId(userV)
                                    .setCcusId(custV)
                                    .setCsupId(supV)
                                    .setProjectClassId(itemClassIndex.get() == -1 || StrUtil.isBlank(excellist.get(finalI)[itemClassIndex.get()].toString())?null:excellist.get(finalI)[itemClassIndex.get()].toString())
                                    .setProjectId(itemIndex.get() == -1 || StrUtil.isBlank(excellist.get(finalI)[itemIndex.get()].toString())?null:excellist.get(finalI)[itemIndex.get()].toString());
                            if (cdfinesIndex.get().keySet().size() > 0){
                                for (String jey : cdfinesIndex.get().keySet()) {
                                    Integer cindex = cdfinesIndex.get().get(jey);
                                    if (cindex > 0 && StrUtil.isNotBlank(excellist.get(finalI)[cindex].toString()))ReflectionUtil.setValue(acc,jey,excellist.get(finalI)[cindex].toString());
                                }
                            }
                            if (StrUtil.isNotBlank(userV) || StrUtil.isNotBlank(custV) || StrUtil.isNotBlank(supV)){
                                acc.setDbillDate(StrUtil.isBlank(excellist.get(finalI)[dateIndex.get()].toString())?null:excellist.get(finalI)[dateIndex.get()].toString());
                                acc.setInoId(StrUtil.isBlank(excellist.get(finalI)[pzNumberIndex.get()].toString())?null:excellist.get(finalI)[pzNumberIndex.get()].toString());
                                acc.setCdigest(StrUtil.isBlank(excellist.get(finalI)[pzzyIndex.get()].toString())?null:excellist.get(finalI)[pzzyIndex.get()].toString());
                            }
                            if(!"0".equals(acc.getMc()) || !"0".equals(acc.getMd())){
                                newlist.add(acc);
                            }
                        }
                    }
                    AtomicReference<Double> md = new AtomicReference<>(0.0);
                    AtomicReference<Double> mc = new AtomicReference<>(0.0);
                    AtomicReference<Double> nds = new AtomicReference<>(0.0);
                    AtomicReference<Double> ncs = new AtomicReference<>(0.0);
                    AtomicReference<Double> nfratMd = new AtomicReference<>(0.0);
                    AtomicReference<Double> nfratMc = new AtomicReference<>(0.0);
                    AtomicReference<Double> cha = new AtomicReference<>(0.0);
                    AtomicReference<String> id = new AtomicReference<>("");

                    AtomicReference<Double> lmd = new AtomicReference<>(0.0);
                    AtomicReference<Double> lmc = new AtomicReference<>(0.0);
                    AtomicReference<Double> lnds = new AtomicReference<>(0.0);
                    AtomicReference<Double> lncs = new AtomicReference<>(0.0);
                    AtomicReference<Double> lnfratMd = new AtomicReference<>(0.0);
                    AtomicReference<Double> lnfratMc = new AtomicReference<>(0.0);
                    return map.get("code").equals("401") ? Mono.just(map) : accvoucherRepository.saveAll(newlist).flatMap(vo->
                         accvoucherRepository.findAllByIyperiodAndCcodeOrderById(vo.getIyear() + "21", vo.getCcode()).collectList()
                                .map(item2 -> {
                                    md.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getMd())).sum());
                                    mc.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getMc())).sum());
                                    lmd.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getLjMd())).sum());
                                    lmc.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getLjMc())).sum());

                                    //????????????
                                    if(StrUtil.isNotBlank(vo.getNdS()) || StrUtil.isNotBlank(vo.getNcS())){
                                        nds.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getNdS())).sum());
                                        ncs.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getNcS())).sum());

                                        lnds.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getLjSlMd())).sum());
                                        lncs.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getLjSlMc())).sum());

                                        nds.set(fmtJiShui(nds.get()));
                                        ncs.set(fmtJiShui(ncs.get()));

                                        lnds.set(fmtJiShui(lnds.get()));
                                        lncs.set(fmtJiShui(lncs.get()));
                                    }
                                    //??????????????????
                                    if(StrUtil.isNotBlank(vo.getNfratMd())){
                                        nfratMd.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getNfratMd())).sum());
                                        nfratMc.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getNfratMc())).sum());

                                        lnfratMd.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getLjWbMd())).sum());
                                        lnfratMc.set(item2.stream().mapToDouble(i -> Double.parseDouble(i.getLjWbMc())).sum());

                                        nfratMd.set(fmtJiShui(nfratMd.get()));
                                        nfratMc.set(fmtJiShui(nfratMc.get()));

                                        lnfratMd.set(fmtJiShui(lnfratMd.get()));
                                        lnfratMc.set(fmtJiShui(lnfratMc.get()));

                                        if(nfratMd.get() != 0.0 && nfratMc.get() != 0.0){
                                            if(nfratMd.get() - nfratMc.get() > 0){
                                                nfratMd.set(nfratMd.get() - nfratMc.get());
                                                nfratMc.set(0.0);
                                            }else if(nfratMd.get() - nfratMc.get() < 0) {
                                                nfratMc.set(nfratMc.get() - nfratMd.get());
                                                nfratMd.set(0.0);
                                            }
                                        }
                                    }

                                    md.set(fmtJiShui(md.get()));
                                    mc.set(fmtJiShui(mc.get()));
                                    if(md.get() != 0.0 && mc.get() != 0.0){
                                        if(md.get() - mc.get() > 0){
                                            md.set(md.get() - mc.get());
                                            mc.set(0.0);
                                        }else if(md.get() - mc.get() < 0) {
                                            mc.set(mc.get() - md.get());
                                            md.set(0.0);
                                        }
                                    }
                                    cha.set(md.get() - mc.get());

                                    if(mc.get() == 0.0){
                                        nds.set(nds.get()-ncs.get());
                                        ncs.set(0.0);
                                        nfratMd.set(nfratMd.get()-nfratMc.get());
                                        nfratMc.set(0.0);
                                    }else if(md.get() == 0.0){
                                        ncs.set(ncs.get()-nds.get());
                                        nds.set(0.0);
                                        nfratMc.set(nfratMc.get()-nfratMd.get());
                                        nfratMd.set(0.0);
                                    }
                                    return item2;
                                })
                                .flatMap(item2 -> accvoucherRepository.findAllByIyperiodAndCcodeOrderById(vo.getIyear() + "00", vo.getCcode()).collectList()
                                .flatMap(v -> {
                                            if(v.size() == 0){
                                                if(cha.get() != 0.0){
                                                    //baocunff
                                                    Accvoucher acc = new Accvoucher();
                                                    acc.setIyperiod(vo.getIyear() + "00")
                                                            .setIyear(vo.getIyear())
                                                            .setImonth("00")
                                                            .setIperiod("00")
                                                            .setCcode(vo.getCcode())
                                                            .setMdF("0")
                                                            .setIbook("0")
                                                            .setLjMd(String.valueOf(lmd.get()))
                                                            .setLjMc(String.valueOf(lmc.get()))
                                                            .setLjWbMd("0")
                                                            .setLjWbMc("0")
                                                            .setLjSlMd("0")
                                                            .setLjSlMc("0")
                                                            .setMd(String.valueOf(md.get()))
                                                            .setMc(String.valueOf(mc.get()))
                                                            .setNdS("0")
                                                            .setNcS("0")
                                                            .setNfratMd("0")
                                                            .setNfratMc("0")
                                                            .setTenantId(databasenum);

                                                    if(StrUtil.isNotBlank(vo.getNdS()) || StrUtil.isNotBlank(vo.getNcS())){
                                                        acc.setNdS(String.valueOf(nds.get()));
                                                        acc.setNcS(String.valueOf(ncs.get()));
                                                        acc.setLjSlMd(String.valueOf(lnds.get()));
                                                        acc.setLjSlMc(String.valueOf(lncs.get()));
                                                        acc.setCunitPrice("0");
                                                    }
                                                    //??????????????????
                                                    if(StrUtil.isNotBlank(vo.getNfratMd())){
                                                        acc.setNfratMd(String.valueOf(nfratMd.get()));
                                                        acc.setNfratMc(String.valueOf(nfratMc.get()));
                                                        acc.setLjWbMd(String.valueOf(lnfratMd.get()));
                                                        acc.setLjWbMc(String.valueOf(lnfratMc.get()));
                                                        acc.setMdF("0");
                                                    }
                                                    return accvoucherRepository.save(acc);
                                                }
                                            }
                                            if(v.size() != 0){
                                                if(cha.get() != 0.0){
                                                    Accvoucher acc = v.stream().findFirst().get();
                                                    acc.setMd(String.valueOf(md.get()));
                                                    acc.setMc(String.valueOf(mc.get()));
                                                    acc.setLjMd(String.valueOf(lmd.get()));
                                                    acc.setLjMc(String.valueOf(lmc.get()));
                                                    if(StrUtil.isNotBlank(vo.getNdS()) || StrUtil.isNotBlank(vo.getNcS())){
                                                        acc.setNdS(String.valueOf(nds.get()));
                                                        acc.setNcS(String.valueOf(ncs.get()));
                                                        acc.setLjSlMd(String.valueOf(lnds.get()));
                                                        acc.setLjSlMc(String.valueOf(lncs.get()));
                                                        acc.setCunitPrice("0");
                                                    }
                                                    //??????????????????
                                                    if(StrUtil.isNotBlank(vo.getNfratMd())){
                                                        acc.setNfratMd(String.valueOf(nfratMd.get()));
                                                        acc.setNfratMc(String.valueOf(nfratMc.get()));
                                                        acc.setLjWbMd(String.valueOf(lnfratMd.get()));
                                                        acc.setLjWbMc(String.valueOf(lnfratMc.get()));
                                                        acc.setMdF("0");
                                                    }
                                                    return accvoucherRepository.save(acc);
                                                }
                                                if(cha.get() == 0.0){
                                                    //baocunff
                                                    Accvoucher acc = v.stream().findFirst().get();
                                                    return accvoucherRepository.delete(acc);
                                                }
                                            }
                                            return Mono.just(vo);
                                        }))
                    );
                })
                .collectList()
                .map(o -> R.ok().setResult(o));
    }
    private Double fmtJiShui(Double md) {
        return md;
    }
    private Integer getCurrNameIndex(String titleName,List<String> titleList) {
        Integer result = -1;
        for (String title : titleList) {
            if (Objects.equals(titleName,title))result = titleList.indexOf(title);
        }
        return result;
    }
}
