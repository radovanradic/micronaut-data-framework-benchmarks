package bench.common.jmh;

import bench.common.dataset.Database;
import bench.common.dataset.DbSettings;
import bench.common.scenario.Workload;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.ThreadParams;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Inserts the row the {@code delete} scenario deletes, before each invocation and outside the measured
 * time, with plain JDBC on a connection outside the framework's pool.
 *
 * <p>{@link Level#Invocation} adds a few timestamps per operation; that is negligible for operations
 * that take tens of microseconds or more, as database operations do.
 */
@State(Scope.Thread)
public class DeleteFixture {

    private Connection connection;
    private Workload workload;
    public long bookId;

    @Setup(Level.Trial)
    public void open(ThreadParams params) throws SQLException {
        connection = new Database(DbSettings.fromEnvironment()).connect();
        workload = new Workload(params.getThreadIndex(), params.getThreadCount(), 'F');
    }

    @Setup(Level.Invocation)
    public void insertRow() throws SQLException {
        bookId = Database.insertFixtureBook(connection, workload.newBook());
    }

    @TearDown(Level.Trial)
    public void close() throws SQLException {
        connection.close();
    }
}
