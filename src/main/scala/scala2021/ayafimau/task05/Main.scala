package scala2021.ayafimau.task05

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object Main extends App {

  val employees = defineEmployees
  val departments = defineDepartments
  val managers = defineManagers

  // findEmployeeManagers.foreach(println)

  // Найти имя менеджера департамента, в котором работает сотрудник по имени сотрудника
  def findManagerName(employee: String): Option[String] = {
    val possibleManager = for {empl <- employees.find(_.name == employee)
                               department <- departments.find(_.id == empl.departmentId)
                               manager <- managers.find(_.department == department.name)
                               managerAsAmpl <- employees.find(_.id == manager.employeeId)
                               } yield managerAsAmpl.name
    possibleManager
  }

  // Найти имя менеджера по имени сотрудника, в случае ошибки в данных - указать что именно не так
  def findManagerNameOrError(employee: String): Either[String, String] = {
    val possibleManager = for {
      empl <- employees.find(_.name == employee) match {
        case Some(x) => Right(x)
        case None => Left(s"Employee with name $employee is not present in the repository")
      }
      department <- departments.find(_.id == empl.departmentId) match {
        case Some(x) => Right(x)
        case None => Left(s"Department with id ${empl.departmentId} (for employee $employee) is not present in the repository")
      }
      manager <- managers.find(_.department == department.name) match {
        case Some(x) => Right(x)
        case None => Left(s"Manager for department ${department.name} (department id =  ${department.id}) is not present in the repository")
      }
      managerAsEmpl <- employees.find(_.id == manager.employeeId) match {
        case Some(x) => Right(x)
        case None => Left(s"Employee with id ${manager.employeeId} who should be manager for department ${department.name} is not present in the repository")
      }
    } yield managerAsEmpl.name

    possibleManager
  }

  // Найти имя менеджера по имени сотрудника, в случае ошибки в данных - указать что именно не так и сделать все это асинхронно
  def `findManagerNameOrErrorAsync original version`(employee: String): Future[Either[String, String]] = {
    val possibleManager = for {
      empl <- Future {
        employees.find(_.name == employee) match {
          case Some(x) => x
          case None => throw new Exception(s"Employee with name $employee is not present in the repository")
        }
      }
      department <- Future {
        departments.find(_.id == empl.departmentId) match {
          case Some(x) => x
          case None => throw new Exception(s"Department with id ${empl.departmentId} (for employee $employee) is not present in the repository")
        }
      }
      manager <- Future {
        managers.find(_.department == department.name) match {
          case Some(x) => x
          case None => throw new Exception(s"Manager for department ${department.name} (department id =  ${department.id}) is not present in the repository")
        }
      }
      managerAsEmpl <- Future {
        employees.find(_.id == manager.employeeId) match {
          case Some(x) => x
          case None => throw new Exception(s"Employee with id ${manager.employeeId} who should be manager for department ${department.name} is not present in the repository")
        }
      }
    } yield managerAsEmpl.name

    possibleManager.transformWith {
      case Success(result) =>
        Future(Right(result))
      case Failure(exception) =>
        Future(Left(exception.getMessage))
    }
  }

  // Найти имя менеджера по имени сотрудника, в случае ошибки в данных - указать что именно не так и сделать все это асинхронно
  def `findManagerNameOrErrorAsync code de-dupe version`(employee: String): Future[Either[String, String]] = {
    findInfoAsync(employee) transformWith {
      case Success(result) =>
        result match {
          case (_, _, Right(managerName)) => Future(Right(managerName))
          case (_, _, Left(managerException)) => Future(Left(managerException))
        }
      case Failure(exception) =>
        Future(Left(exception.getMessage))
    }
  }

  // returns (employeeName, Either department name or exception,  Either manager name or exception)
  def findInfoAsync(employee: String): Future[(String, Either[String, String], Either[String, String])] = {
    val possibleDepartment = for {
      empl <- Future {
        employees.find(_.name == employee) match {
          case Some(x) => x
          case None => throw new Exception(s"Employee with name $employee is not present in the repository")
        }
      }
      department <- Future {
        departments.find(_.id == empl.departmentId) match {
          case Some(x) => x
          case None => throw new Exception(s"Department with id ${empl.departmentId} (for employee $employee) is not present in the repository")
        }
      }
    } yield department

    val result = possibleDepartment.transformWith {
      case Failure(departmentException) =>
        Future((employee, Left(departmentException.getMessage), Left(departmentException.getMessage)))
      case Success(resultDepartment) =>
        val possibleManager = for {
          manager <- Future {
            managers.find(_.department == resultDepartment.name) match {
              case Some(x) => x
              case None => throw new Exception(s"Manager for department ${resultDepartment.name} (department id =  ${resultDepartment.id}) is not present in the repository")
            }
          }
          managerAsEmpl <- Future {
            employees.find(_.id == manager.employeeId) match {
              case Some(x) => x
              case None => throw new Exception(s"Employee with id ${manager.employeeId} who should be manager for department ${resultDepartment.name} is not present in the repository")
            }
          }
        } yield managerAsEmpl.name

        possibleManager.transformWith {
          case Failure(managerException) =>
            Future((employee, Right(resultDepartment.name), Left(managerException.getMessage)))
          case Success(result) =>
            Future((employee, Right(resultDepartment.name), Right(result)))
        }
    }
    result
  }

  // вывести список всех сотрудников, вместе с именем департамента и именем менеджера, если департамента или менеджера нет то использовать константу "Not Found"
  def findEmployeeManagers: List[Info] = {
    val notFound = "Not Found"

    val employeeNames = employees.map(_.name)

    val infoListFuture = Future.traverse(employeeNames)(findInfoAsync)
    val infoList = Await.result(infoListFuture, Duration.Inf)

    infoList.map {
      case (name, Left(_), Left(_)) => Info(name, notFound, notFound)
      case (name, Right(department), Left(_)) => Info(name, department, notFound)
      case (name, Right(department), Right(manager)) => Info(name, department, manager)
    }
  }

  case class Employee(id: Int, name: String, departmentId: Int)
  case class Department(id: Int, name: String)
  case class Manager(department: String, employeeId: Int)
  case class Info(employee: String, department: String, manager: String)

  private def defineEmployees = {
    List(
      Employee(1, "Steve", 1),
      Employee(3, "Mark", 1),
      Employee(4, "Jane", 1),
      Employee(7, "Samuel", 2),
      Employee(10, "Igor", 2),
      Employee(11, "Naveen", 4),
      Employee(12, "Christy", 5),
      Employee(15, "Megan", 3)
    )
  }

  private def defineDepartments = {
    List(
      Department(1, "Marketing"),
      Department(2, "Sales"),
      Department(3, "Research"),
      Department(4, "IT"),
    )
  }

  private def defineManagers = {
    List(
      Manager("Marketing", 1),
      Manager("Sales", 10),
      Manager("IT", 14),
    )
  }

}
