package com.sogeco.fleet.modules.expense;

import com.sogeco.fleet.common.enums.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = {"vehicle", "driver", "agency"})
    Page<Expense> findAllBy(Pageable pageable);

    List<Expense> findByMissionId(Long missionId);

    @EntityGraph(attributePaths = {"vehicle", "agency"})
    List<Expense> findByVehicleIdOrderByExpenseDateDesc(Long vehicleId);

    @Query("""
           SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
           WHERE e.expenseDate >= :from AND e.expenseDate <= :to
           """)
    BigDecimal totalAmount(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
           WHERE e.category = :category
             AND e.expenseDate >= :from AND e.expenseDate <= :to
           """)
    BigDecimal totalByCategory(@Param("category") ExpenseCategory category,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Repartition par poste, pour l'anneau du tableau de bord. */
    @Query("""
           SELECT e.category, COUNT(e), COALESCE(SUM(e.amount), 0)
           FROM Expense e
           WHERE e.expenseDate >= :from AND e.expenseDate <= :to
           GROUP BY e.category
           ORDER BY SUM(e.amount) DESC
           """)
    List<Object[]> aggregateByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
           WHERE e.vehicle.id = :vehicleId
             AND e.expenseDate >= :from AND e.expenseDate <= :to
           """)
    BigDecimal totalForVehicle(@Param("vehicleId") Long vehicleId,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT e.agency.id, e.agency.name, COALESCE(SUM(e.amount), 0)
           FROM Expense e
           WHERE e.agency IS NOT NULL
             AND e.expenseDate >= :from AND e.expenseDate <= :to
           GROUP BY e.agency.id, e.agency.name
           """)
    List<Object[]> aggregateByAgency(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
