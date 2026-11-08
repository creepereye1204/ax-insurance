package com.ax.core.agent;

import com.ax.core.common.Region;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRepository agentRepository;

    public record Response(Long id, String code, String name, AgentChannel channel, String channelLabel, String branch,
                           Region region, String regionLabel, LocalDate hiredOn) {
        public static Response from(Agent a) {
            return new Response(a.getId(), a.getCode(), a.getName(), a.getChannel(), a.getChannel().getLabel(), a.getBranch(),
                    a.getRegion(), a.getRegion().getLabel(), a.getHiredOn());
        }
    }

    public record RegionResponse(String code, String label) {
    }

    /** 재직 중인 설계사 (지역·지점 순) — 가입설계 화면의 담당 설계사 선택용 */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list() {
        return agentRepository.findByActiveTrueOrderByRegionAscBranchAscNameAsc().stream().map(Response::from).toList();
    }

    @GetMapping("/regions")
    public List<RegionResponse> regions() {
        return Arrays.stream(Region.values()).map(r -> new RegionResponse(r.name(), r.getLabel())).toList();
    }
}
