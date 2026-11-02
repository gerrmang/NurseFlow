package com.nurseflow.hospital.domain

/** 병원 단위 역할. 병동 역할(`WARD_ADMIN`, `WARD_MEMBER`)과 별개이며 서로 상속하지 않는다. */
enum class HospitalRole { HOSPITAL_ADMIN }

/** 병원 리소스에 대한 현재 사용자의 접근 수준. */
enum class HospitalAccess {
    /** 병원 관리자. 병원 정책·병동 생성 권한. */
    ADMIN,

    /** 병원 소속 병동의 구성원이지만 병원 관리자가 아니다. 병원의 존재는 알 수 있으나 병원 작업은 불가. */
    AFFILIATED,

    /** 병원과 무관하거나 병원이 없다. 존재 여부를 노출하지 않는다. */
    NONE,
    ;

    companion object {
        /**
         * 병원 역할은 `hospital_memberships`에서만 나온다. 병동 관리자 역할은 병원 권한으로 확장되지 않는다.
         */
        fun decide(hospitalRoles: Set<HospitalRole>, hasWardMembershipInHospital: Boolean): HospitalAccess = when {
            HospitalRole.HOSPITAL_ADMIN in hospitalRoles -> ADMIN
            hasWardMembershipInHospital -> AFFILIATED
            else -> NONE
        }
    }
}
