package com.ax.core.agent;

import com.ax.core.common.BaseTimeEntity;
import com.ax.core.common.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 설계사 (모집인). 설계서·청약·계약에 담당 설계사로 남아 정보계의 설계사·지점 차원이 된다. 모두 가상 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AgentChannel channel;

    /** 소속 지점 (GA 는 대리점명) */
    @Column(nullable = false, length = 50)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Column(nullable = false)
    private LocalDate hiredOn;

    @Column(nullable = false)
    private boolean active;

    public Agent(String code, String name, AgentChannel channel, String branch, Region region, LocalDate hiredOn) {
        this.code = code;
        this.name = name;
        this.channel = channel;
        this.branch = branch;
        this.region = region;
        this.hiredOn = hiredOn;
        this.active = true;
    }
}
