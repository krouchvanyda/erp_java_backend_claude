package com.company.erp.features.employees.controller;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.response.PageResponse;
import com.company.erp.core.security.AuthenticatedUser;
import com.company.erp.core.security.Permissions;
import com.company.erp.features.employees.dto.CreateEmployeeRequest;
import com.company.erp.features.employees.dto.EmployeeDto;
import com.company.erp.features.employees.dto.UpdateEmployeeRequest;
import com.company.erp.features.employees.service.EmployeeAvatarService;
import com.company.erp.features.employees.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employees;
    private final EmployeeAvatarService avatars;

    public EmployeeController(EmployeeService employees, EmployeeAvatarService avatars) {
        this.employees = employees;
        this.avatars = avatars;
    }

    @GetMapping("/me")
    public EmployeeDto me() {
        Long userId = AuthenticatedUser.require().userId();
        return EmployeeDto.from(employees.getByUserId(userId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_READ + "')")
    public PageResponse<EmployeeDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(
                employees.list(new PageQuery(page, pageSize, search, sort)),
                EmployeeDto::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_READ + "')")
    public EmployeeDto get(@PathVariable Long id) {
        return EmployeeDto.from(employees.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_WRITE + "')")
    public EmployeeDto create(@Valid @RequestBody CreateEmployeeRequest body) {
        return EmployeeDto.from(employees.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_WRITE + "')")
    public EmployeeDto update(@PathVariable Long id,
                              @Valid @RequestBody(required = false) UpdateEmployeeRequest body) {
        if (body == null) return EmployeeDto.from(employees.getById(id));
        return EmployeeDto.from(employees.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_WRITE + "')")
    public void delete(@PathVariable Long id) {
        employees.delete(id);
    }

    @PostMapping(path = "/me/avatar", consumes = "multipart/form-data")
    public EmployeeDto uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = AuthenticatedUser.require().userId();
        return EmployeeDto.from(avatars.uploadForCurrentUser(userId, file));
    }

    @DeleteMapping("/me/avatar")
    public EmployeeDto deleteMyAvatar() {
        Long userId = AuthenticatedUser.require().userId();
        return EmployeeDto.from(avatars.deleteForCurrentUser(userId));
    }

    @PostMapping(path = "/{id}/avatar", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_WRITE + "')")
    public EmployeeDto uploadAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return EmployeeDto.from(avatars.uploadForEmployee(id, file));
    }

    @DeleteMapping("/{id}/avatar")
    @PreAuthorize("hasAuthority('" + Permissions.EMPLOYEE_WRITE + "')")
    public EmployeeDto deleteAvatar(@PathVariable Long id) {
        return EmployeeDto.from(avatars.deleteForEmployee(id));
    }
}
