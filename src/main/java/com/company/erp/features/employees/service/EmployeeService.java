package com.company.erp.features.employees.service;

import com.company.erp.core.database.PageQuery;
import com.company.erp.core.exceptions.ConflictException;
import com.company.erp.core.exceptions.NotFoundException;
import com.company.erp.features.employees.dto.CreateEmployeeRequest;
import com.company.erp.features.employees.dto.UpdateEmployeeRequest;
import com.company.erp.features.employees.entity.Employee;
import com.company.erp.features.employees.entity.EmployeeStatus;
import com.company.erp.features.employees.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@Transactional
public class EmployeeService {

    private static final Set<String> ALLOWED_SORT =
            Set.of("employeeNo", "fullName", "department", "hireDate", "status", "createdAt");

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public Page<Employee> list(PageQuery query) {
        return employees.search(query.search(),
                query.toPageable(ALLOWED_SORT, Sort.by("fullName")));
    }

    @Transactional(readOnly = true)
    public Employee getById(Long id) {
        return employees.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    @Transactional(readOnly = true)
    public Employee getByUserId(Long userId) {
        return employees.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Employee profile not found for current user"));
    }

    /**
     * Ensures the given login user has a linked employee profile, creating a
     * minimal one from the user's own details when none exists yet. Idempotent:
     * returns the existing profile untouched if the user is already linked.
     *
     * <p>Called when a user account is created (and as a login-time safety net)
     * so {@code GET /employees/me} never 404s for a freshly-created account —
     * which previously left the mobile app's profile screen blank.
     */
    public Employee ensureProfileForUser(Long userId, String fullName, String email, String phone) {
        return employees.findByUserId(userId).orElseGet(() -> {
            Employee e = new Employee();
            e.setUserId(userId);
            e.setEmployeeNo(generateEmployeeNo(userId));
            // employees.full_name is NOT NULL — users.full_name is @NotBlank,
            // so this is always present, but fall back defensively anyway.
            e.setFullName((fullName != null && !fullName.isBlank()) ? fullName : email);
            e.setWorkEmail(email);
            e.setPhone(phone);
            e.setStatus(EmployeeStatus.ACTIVE);
            return employees.save(e);
        });
    }

    /**
     * Deterministic, unique employee number derived from the user id
     * ({@code EMP-00015}). One employee per user, so the user id keeps it
     * unique; the existence check only guards against a pre-existing manual
     * row that happened to claim the same number.
     */
    private String generateEmployeeNo(Long userId) {
        String candidate = String.format("EMP-%05d", userId);
        if (employees.existsByEmployeeNo(candidate)) {
            candidate = candidate + "-" + System.currentTimeMillis();
        }
        return candidate;
    }

    public Employee create(CreateEmployeeRequest req) {
        if (employees.existsByEmployeeNo(req.employeeNo())) {
            throw new ConflictException("Employee number already in use");
        }
        if (req.userId() != null && employees.existsByUserId(req.userId())) {
            throw new ConflictException("This user is already linked to another employee profile");
        }

        Employee e = new Employee();
        e.setUserId(req.userId());
        e.setEmployeeNo(req.employeeNo());
        e.setFullName(req.fullName());
        e.setWorkEmail(req.workEmail());
        e.setPhone(req.phone());
        e.setPosition(req.position());
        e.setDepartment(req.department());
        e.setHireDate(req.hireDate());
        e.setDateOfBirth(req.dateOfBirth());
        e.setGender(req.gender());
        e.setAddress(req.address());
        e.setAvatarUrl(req.avatarUrl());
        e.setEmergencyContact(req.emergencyContact());
        e.setEmergencyPhone(req.emergencyPhone());
        e.setStatus(req.status() != null ? req.status() : EmployeeStatus.ACTIVE);
        return employees.save(e);
    }

    public Employee update(Long id, UpdateEmployeeRequest req) {
        Employee e = getById(id);

        if (req.employeeNo() != null && !req.employeeNo().equals(e.getEmployeeNo())) {
            if (employees.existsByEmployeeNo(req.employeeNo())) {
                throw new ConflictException("Employee number already in use");
            }
            e.setEmployeeNo(req.employeeNo());
        }
        if (req.userId() != null && !req.userId().equals(e.getUserId())) {
            if (employees.existsByUserId(req.userId())) {
                throw new ConflictException("This user is already linked to another employee profile");
            }
            e.setUserId(req.userId());
        }
        if (req.fullName()    != null) e.setFullName(req.fullName());
        if (req.workEmail()   != null) e.setWorkEmail(req.workEmail());
        if (req.phone()       != null) e.setPhone(req.phone());
        if (req.position()    != null) e.setPosition(req.position());
        if (req.department()  != null) e.setDepartment(req.department());
        if (req.hireDate()    != null) e.setHireDate(req.hireDate());
        if (req.dateOfBirth() != null) e.setDateOfBirth(req.dateOfBirth());
        if (req.gender()      != null) e.setGender(req.gender());
        if (req.address()     != null) e.setAddress(req.address());
        if (req.avatarUrl()        != null) e.setAvatarUrl(req.avatarUrl());
        if (req.emergencyContact() != null) e.setEmergencyContact(req.emergencyContact());
        if (req.emergencyPhone()   != null) e.setEmergencyPhone(req.emergencyPhone());
        if (req.status()           != null) e.setStatus(req.status());
        return e;
    }

    public void delete(Long id) {
        employees.delete(getById(id));
    }

    /**
     * Updates {@code last_login_at} for the employee linked to the given user, if any.
     * Silent when no employee is linked — callers shouldn't fail login over this.
     */
    public void touchLastLoginByUserId(Long userId) {
        employees.findByUserId(userId)
                .ifPresent(e -> e.setLastLoginAt(Instant.now()));
    }
}
