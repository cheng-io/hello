package org.boozsoft.rest.stock;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.boozsoft.domain.entity.stock.StockDefineHead;
import org.boozsoft.domain.entity.stock.StockDefineReceiptBody;
import org.boozsoft.domain.entity.stock.StockDefineReceiptHead;
import org.boozsoft.repo.stock.StockDefineReceiptBodyRepository;
import org.boozsoft.repo.stock.StockDefineReceiptHeadRepository;
import org.boozsoft.util.XlsUtils3;
import org.springbooz.core.tool.result.R;
import org.springbooz.core.tool.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/stockDefineReceiptBody")
public class StockDefineReceiptBodyController {

    @Autowired
    StockDefineReceiptBodyRepository stockDefineReceiptBodyRepository;
    @Autowired
    StockDefineReceiptHeadRepository stockDefineReceiptHeadRepository;

    @PostMapping("stockDefineBodyFindByAll")
    public Mono<R> stockDefineBodyFindByAll(String headName){
        return stockDefineReceiptBodyRepository.findAllByDefineHeadName(headName).collectList().map(a->R.ok().setResult(a));
    }

    @PostMapping("stockDefineBodySave")
    public Mono<R> stockDefineBodySave(@RequestBody StockDefineReceiptBody pojo){
        return stockDefineReceiptBodyRepository.save(pojo).map(a->R.ok().setResult(a));
    }

    @PostMapping("stockDefineBodyDelById")
    public Mono<R> stockDefineBodyDelById(@RequestBody Map map){
        List<String> list= Arrays.asList(map.get("id").toString().split(","));
        return stockDefineReceiptBodyRepository.delById(list).then(Mono.just(R.ok()));
    }
    @PostMapping("stockDefineBodyDelByHeadId")
    public Mono<R> stockDefineBodyDelByHeadId(String headId){
        return stockDefineReceiptBodyRepository.delByHeadId(headId).then(Mono.just(R.ok()));
    }

    @PostMapping("/importStockDefineBody")
    public Mono<R> importStockDefineBody(@RequestPart("file") FilePart filePartParm) throws Exception {
        // ??????????????????
        AtomicInteger column= new AtomicInteger();
        Path tempFilePath = Files.createTempFile("", new String(filePartParm.filename().getBytes("ISO-8859-1"), "UTF-8"));
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
            .flatMap(objArr -> {
                List<Object[]> list = null;
                XlsUtils3 excelReader = new XlsUtils3(tempFilePath.toString());
                list = excelReader.getExcelObj(tempFilePath.toString(), new String[]{"??????????????????", "????????????", "????????????",""}, 0);
                Arrays.stream(list.get(0)).forEach(v->{
                    if(v!=null){
                        column.addAndGet(1);
                    }
                });
                assert tempFilePath != null;
                try {
                    Files.delete(tempFilePath);
                } catch (IOException e) {}
                return Mono.just(list);
            })
            .flatMap(item -> {
                Map mapArr = new HashMap();
                mapArr.put("excellist", item);   // excel?????????
                mapArr.put("error", "");
                mapArr.put("code", "200");
                return stockDefineReceiptBodyRepository.findAll().collectList().flatMap(ite -> {
                    mapArr.put("bodylist", ite);
                    return stockDefineReceiptHeadRepository.findAll().collectList()
                        .flatMap(headlist->{
                            mapArr.put("headlist", headlist);
                            return Mono.just(mapArr);
                        });
                });
            })
            .flatMap(mapArr -> {
                List<StockDefineReceiptBody> newlist=new ArrayList<>();

                List<StockDefineReceiptBody> bodylist = (List<StockDefineReceiptBody>) mapArr.get("bodylist");
                List<StockDefineReceiptHead> headlist = (List<StockDefineReceiptHead>) mapArr.get("headlist");
                List<Object[]> excellist = (List<Object[]>) mapArr.get("excellist");
                // ??????????????????
                for (int i = 1; i < excellist.size(); i++) {
                    int a=column.get();
                    Object[] obj=excellist.get(i);
                    int finalI = i;
                    List<String>errorText=new ArrayList<>();
                    // ??????????????????
                    List<StockDefineReceiptHead> collect = headlist.stream().filter(v -> v.getDefineName().equals(excellist.get(finalI)[0].toString().trim())).collect(Collectors.toList());
                    if(StringUtils.isBlank(excellist.get(finalI)[0].toString())) {
                        errorText.add("??????????????????????????????");
                        obj[a]=errorText.toString();
                    }else{
                        if(collect.size()==0){
                            errorText.add("???????????????????????????????????????");
                            obj[a]=errorText.toString();
                        }
                    }
                    // ????????????
                    if(StringUtils.isBlank(excellist.get(finalI)[1].toString())) {
                        errorText.add("????????????????????????");
                        obj[a]=errorText.toString();
                    }else{
                        String str="?????????,??????,??????,??????";
                        List<String> list =Arrays.asList(str.split(","));
                        List<String> collect1 = list.stream().filter(v -> v.equals(excellist.get(finalI)[1].toString().trim())).collect(Collectors.toList());
                        if(collect1.size()==0){
                            errorText.add("????????????????????????????????????");
                            obj[a]=errorText.toString();
                        }
                    }

                    // ?????????????????? + ????????????
                    if(StringUtils.isNotBlank(excellist.get(finalI)[0].toString()) && StringUtils.isNotBlank(excellist.get(finalI)[1].toString())) {
                        List<StockDefineReceiptHead> collect1 = collect.stream().filter(v -> v.getDefineName().equals(excellist.get(finalI)[0].toString().trim()) && v.getDefineType().equals(excellist.get(finalI)[1].toString().trim())).collect(Collectors.toList());
                        if(collect1.size()==0){
                            errorText.add("???????????????????????????????????????????????????");
                            obj[a]=errorText.toString();
                        }
                    }

                    // ????????????
                    if(StringUtils.isBlank(excellist.get(finalI)[2].toString())) {
                        errorText.add("????????????????????????");
                        obj[a]=errorText.toString();
                    }else{
                        if(collect.size()>0){
                            // ?????????????????? + ????????????  ????????????
                            List<StockDefineReceiptBody> collect1 = bodylist.stream().filter(v -> v.getDefineHeadId().equals(collect.get(0).getId()) && v.getDeName().equals(excellist.get(finalI)[2].toString().trim())).collect(Collectors.toList());
                            if(collect1.size()>0){
                                errorText.add("?????????????????????????????????");
                                obj[a]=errorText.toString();
                            }
                        }
                    }

                    if(errorText.size()==0){
                        StockDefineReceiptBody b=new StockDefineReceiptBody();
                        b.setDeType(excellist.get(finalI)[1].toString().trim())
                        .setDefineHeadId(collect.get(0).getId())
                        .setDeName(excellist.get(finalI)[2].toString().trim());
                        newlist.add(b);
                    }else{ mapArr.put("code", "200");mapArr.put("error", "error");}
                }

                // ????????????
                List<String> listStr=new ArrayList<>();
                for (int i = 1; i < excellist.size(); i++) {
                    if(StrUtil.isNotBlank(excellist.get(i)[0].toString().trim()) && StrUtil.isNotBlank(excellist.get(i)[2].toString().trim())){
                        listStr.add(excellist.get(i)[0].toString().trim()+excellist.get(i)[2].toString().trim());
                    }
                }
                // ?????????list
                long doublesize1=listStr.stream().distinct().count();
                if(doublesize1<listStr.size()){
                    mapArr.put("error", "?????????????????????????????????????????????????????????");
                    mapArr.put("code", "401");
                    return Mono.just(mapArr);
                }
                mapArr.put("list", newlist);
                mapArr.put("column", column.get());
                return Mono.just(mapArr);
            })
            .flatMapMany(mapArr -> {
                List<StockDefineReceiptBody> newlist= (List<StockDefineReceiptBody>) mapArr.get("list");
                return mapArr.get("code").equals("401")?Mono.just(mapArr):newlist.size()==0?Mono.just(mapArr):stockDefineReceiptBodyRepository.saveAll(newlist).map(t->mapArr);
            })
            .collectList()
            .map(o -> R.ok().setResult(o));
    }

    @PostMapping("/countBodyByHeadIdAndDeName")
    public Mono<R> countBodyByHeadIdAndDeName(String headId,String deName){
        return stockDefineReceiptBodyRepository.countByDefineHeadIdAndDeName(headId,deName).map(a->R.ok().setResult(a));
    }

}
