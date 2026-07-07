package vn.t3nexus.inventory.infrastructure.persistence.reservation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reservation_item")
@Getter
@Setter
@NoArgsConstructor
public class ReservationItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 26)
    private String id;

    @Column(name = "reservation_id", nullable = false, updatable = false, length = 26)
    private String reservationId;

    @Column(name = "sku_id", nullable = false, updatable = false, length = 26)
    private String skuId;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;
}
