package org.boozsoft.rest.group;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.ApiOperation;
import org.boozsoft.domain.entity.SysJobFile;
import org.boozsoft.domain.entity.SysJobType;
import org.boozsoft.domain.entity.group.GroupSysJobFile;
import org.boozsoft.domain.entity.group.GroupSysJobType;
import org.boozsoft.domain.vo.SysJobFileTreeVo;
import org.boozsoft.domain.vo.group.GroupExpenditureClassVo;
import org.boozsoft.repo.SysJobFileRepository;
import org.boozsoft.repo.SysJobTypeRepository;
import org.boozsoft.repo.group.GroupSysJobFileRepository;
import org.boozsoft.repo.group.GroupSysJobTypeRepository;
import org.boozsoft.util.TreeUtils;
import org.boozsoft.utils.CollectOfUtils;
import org.springbooz.core.tool.result.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/group/jobfile")
public class GroupSysJobFileController {

    @Autowired
    private GroupSysJobFileRepository sysJobFileRepository;
    @Autowired
    private GroupSysJobTypeRepository sysJobTypeRepository;


    @GetMapping("treeDept")
    public Mono<Map> treeDept(String id, String flag) {
        if (StrUtil.isNotBlank(id) && !id.equals("0")) {
            return sysJobFileRepository.findByIdOrderByDeptCode(id)
                    .collectList()
//                    .map(list->TreeUtils.castTreeList(list,GroupExpenditureClassVo.class))
                    .map(list -> {
                        if (StrUtil.isNotBlank(flag)) {
                            return list.stream().filter(item -> flag.contains(item.getFlag())).collect(Collectors.toList());
                        }
                        return list;
                    })
                    .map(list -> CollectOfUtils.mapof(
                            "code", 0,
                            "message", "ok",
                            "type", "success",
                            "result", list
                    ));
        } else {
            return sysJobFileRepository.findAllByOrderByEcCode()
                    .collectList()
                    .map(list -> TreeUtils.castTreeList(list, GroupExpenditureClassVo.class))
                    .map(list -> {
                        if (StrUtil.isNotBlank(flag)) {
                            return list.stream().filter(item -> flag.contains(item.getFlag())).collect(Collectors.toList());
                        }
                        return list;
                    })
                    .map(list -> CollectOfUtils.mapof(
                            "code", 0,
                            "message", "ok",
                            "type", "success",
                            "result", list
                    ));
        }
    }

    @GetMapping("treeDeptByFlag")
    public Mono<Map> treeDeptByFlag() {
        return sysJobTypeRepository.findByFlagOrderByEcCode("1")
                .collectList()
                .map(list->TreeUtils.castTreeList(list, SysJobFileTreeVo.class))
                .map(list -> CollectOfUtils.mapof(
                        "code", 0,
                        "message", "ok",
                        "type", "success",
                        "result", list
                ));
    }

    @GetMapping("treeDeptByIsEnd")
    public Mono<Map> treeDeptByIsEnd() {
        return sysJobFileRepository.findByFlagAndBendOrderByEcCode("1","1")
                .collectList()
                .map(list->TreeUtils.castTreeList(list, SysJobFileTreeVo.class))
                .map(list -> CollectOfUtils.mapof(
                        "code", 0,
                        "message", "ok",
                        "type", "success",
                        "result", list
                ));
    }

    @GetMapping("findAll")
    @ApiOperation(value = "????????????", notes = "??????code")
    public Mono<R> findAll(){
        return sysJobFileRepository.findAllByOrderByEcCode().collectList().map(R::page);
    }

    @GetMapping("getTypeList")
    @ApiOperation(value = "????????????", notes = "??????code")
    public Mono<R> getTypeList(){
        return sysJobTypeRepository.findAllByOrderByEcCode().collectList().map(R::ok);
    }

    @GetMapping("all")
    @ApiOperation(value = "????????????", notes = "??????code")
    public Mono<R> findAllDept(){
        return sysJobFileRepository.findAllDeptCodeOrDeptNameByFlag().collectList().map(R::ok);
    }

    @GetMapping("findById")
    @ApiOperation(value = "??????", notes = "??????code")
    public Mono findById(String id){
        Mono mono = sysJobFileRepository.findById(id);
        return mono;
    }

    @PostMapping("save")
    @ApiOperation(value = "???????????????", notes = "??????code")
    public Mono save(@RequestBody GroupSysJobFile object){

        if (object.getFlag()==null || object.getFlag().equals("")){
            object.setFlag("1");
        }


        if (object.getCreateDate()==null || object.getCreateDate().equals("")){
            object.setCreateDate(LocalDate.now().toString());
        }

        if (Objects.nonNull(object.getEstDate())){
            object.setEstDate(object.getEstDate().split(" ")[0]);
        }

        //?????????????????????
        Mono<SysJobFile> sysDeptClassMono = null;

        //??????
        if (object.getId()==null || object.getId().equals("")) {
            return Objects.isNull(sysDeptClassMono)? sysJobFileRepository.save(object)
                    .map(o -> R.ok().setResult(o)):sysDeptClassMono.zipWith(sysJobFileRepository.save(object)
                    .map(o -> R.ok().setResult(o))).thenReturn(R.ok());
        }

        return Objects.isNull(sysDeptClassMono)? sysJobFileRepository.save(object)
                .map(o -> R.ok().setResult(o)):sysDeptClassMono.zipWith(sysJobFileRepository.save(object)
                .map(o -> R.ok().setResult(o))).thenReturn(R.ok());
    }

    @PostMapping("saveJobType")
    @ApiOperation(value = "???????????????", notes = "??????code")
    public Mono saveJobType(@RequestBody GroupSysJobType object){

        if (object.getUniqueCode()==null || object.getUniqueCode().equals("")){
            object.setUniqueCode(IdUtil.objectId());
        }
        if (object.getFlag()==null || object.getFlag().equals("")){
            object.setFlag("1");
        }

        //1??? ?????????
        if (object.getParentId()==null || object.getParentId().equals("")){
            object.setParentId("0");
            object.setBend("1");
        }

        if (object.getCreateDate()==null || object.getCreateDate().equals("")){
            object.setCreateDate(LocalDate.now().toString());
        }

        //????????????????????????
        if(object.getParentId()!=null && !object.getParentId().equals("") && object.getBend().equals("0")){
            object.setBend("1");
        }
        //?????????????????????
        Mono<GroupSysJobType> sysDeptClassMono = null;
        if(object.getParentId()!=null && !object.getParentId().equals("") && !object.getParentId().equals("0") && object.getBend().equals("1")){
            object.setBend("1");
            sysDeptClassMono = sysJobTypeRepository.findById(object.getParentId())
                    .map(v -> v.setBend("0"))
                    .flatMap(sysJobTypeRepository::save);
        }
        //??????
        if (object.getId()==null || object.getId().equals("")) {
            return Objects.isNull(sysDeptClassMono)? sysJobTypeRepository.save(object)
                    .map(o -> R.ok().setResult(o)):sysDeptClassMono.zipWith(sysJobTypeRepository.save(object)
                    .map(o -> R.ok().setResult(o))).thenReturn(R.ok());
        }

        return Objects.isNull(sysDeptClassMono)? sysJobTypeRepository.save(object)
                .map(o -> R.ok().setResult(o)):sysDeptClassMono.zipWith(sysJobTypeRepository.save(object)
                .map(o -> R.ok().setResult(o))).thenReturn(R.ok());
    }

    @DeleteMapping("del")
    @ApiOperation(value = "??????", notes = "??????code")
    public Mono delete(@RequestBody GroupSysJobFile object) {
        return sysJobFileRepository.findByIdOrderByDeptCode(object.getId())
                .collectList()
                .flatMap(item -> sysJobFileRepository.deleteAll(item))
                .then(Mono.just(R.ok()));
    }

    @PostMapping("editFlag")
    @ApiOperation(value = "??????????????????", notes = "??????code")
    public Mono editFlag(@RequestBody GroupSysJobFile object) {
        if (object.getFlag().equals("1")){
            object.setFlag("0");
        } else {
            object.setFlag("1");
        }
        return sysJobFileRepository.save(object)
                .map(o -> R.ok().setResult(o));
    }

    @PostMapping("excel")
    @ApiOperation(value = "????????????excel,????????????????????????", notes = "??????code")
    @Transactional
    public Mono excel(@RequestBody List<GroupSysJobFile> object){

        return sysJobFileRepository.saveAll(object)
                .collectList()
                .map(o-> R.ok().setResult(o));
    }

    @GetMapping("/getMaxCode")
    @ApiOperation(value = "????????????code", notes = "????????????code")
    public Mono getMaxCode(String id){
        return sysJobTypeRepository.findMaxCodeByPid(id)
                .map(v->{
                    return (Integer.valueOf(v)+1)+"";
                })
                .defaultIfEmpty("0")
                .map(v-> R.ok().setResult(v));
    }

    @GetMapping("/delTypeById")
    @ApiOperation(value = "???????????????", notes = "???????????????")
    public Mono delTypeById(String id){
        return sysJobTypeRepository.findById(id)
                .flatMap(v->{
                    //
                    return "1".equals(v.getBend())? sysJobTypeRepository.deleteById(id).map(o->R.ok().setResult(o)): Mono.just("");
                })
                .defaultIfEmpty("0")
                .map(v-> R.ok().setResult(v));
    }
}
