<div class="post_blanco">
	<table>
	<thead> <tr >
		<th width='5%'>Rank</th>
		<th width='5%'>Usuario</th>
		<th width='15%'>Ubicación</th>
		<th width='15%'><a href="rank.php?order=escuela">Escuela</a></th>
		<th width='5%'><a href="rank.php?order=resueltos">Resueltos</a></th>
		<th width='5%'><a href="rank.php?order=envios">Envíos</a></th>
		<th width='5%'>Radio</th>
		</tr>
	</thead>
	<tbody>
	<?php
		$res = c_usuario::rank();
		$rank = $res["rank"];
		for ($n = 0; $n < sizeof($rank); $n++)
		{
			$user = $rank[$n];

			if ($n %2 ==0) {
				echo "<TR style='background:#e7e7e7;' align=center>";
			} else {
				echo "<TR align=center>";
			}
			echo "<TD align='center' >". ($n+1) ."</TD>";
			switch ($user["cuenta"])
			{
				case "ADMIN":
					echo "<TD ><a href=\"runs.php?user=". $user["userID"]."\">". $user["userID"] ."(A)</a></TD>";
				break;

				case "OWNER":
					echo "<TD ><a href=\"runs.php?user=". $user["userID"]."\">". $user["userID"] ."(O)</a></TD>";
				break;

				default:
					echo "<TD ><a href=\"runs.php?user=". $user["userID"]."\">". $user["userID"] ."</a></TD>";
			}
			echo "<TD >". $user["ubicacion"] ."</TD>";
			echo "<TD >". $user["escuela"] ."</TD>";
			echo "<TD >". $user["solved"] ."</TD>";
			echo "<TD >". $user["tried"] ."</TD>";

			$ratio =  ($user['solved'] / ($user['tried']+1))*100 ;
			printf("<TD align='center' > %2.2f%% </TD>", $ratio);
			echo "</tr>";
		}
	?>
	</tbody>
	</table>
</div>
